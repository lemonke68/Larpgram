#!/usr/bin/env bash
#
# Larpgram: bring in a new Element X Android release without importing Element's git history.
#
# The public history starts from a single "Import Element X Android vX" commit, and every later
# upstream update lands as one squashed "sync" commit. Both carry an `Upstream-Tag: <tag>` trailer.
# Locally, `git replace --graft` re-attaches those commits to the real upstream tags, so git knows the
# correct merge base and conflicts are only the ones our changes actually cause. Replace refs are
# never pushed, so GitHub only ever sees our own commits.
#
# Usage:
#   tools/larpgram/sync-upstream.sh restore        # re-create local grafts (fresh clone, new machine)
#   tools/larpgram/sync-upstream.sh start <tag>    # e.g. v26.09.2: fetch, squash-merge, stop for conflicts
#   tools/larpgram/sync-upstream.sh finish [msg]   # after resolving conflicts: commit + graft
#                                                  # (msg: optional file with the commit message body)
#   tools/larpgram/sync-upstream.sh check          # before pushing: no Element commit is a real ancestor
#   tools/larpgram/sync-upstream.sh unmarked       # Element .kt files we changed without a `Правка форка` note
#
# Never `git commit --amend` a sync commit: with the grafts active, amend copies the grafted upstream
# parent into the real commit and the next push would upload Element's whole history. Use `finish msg`.
#
set -euo pipefail

UPSTREAM_URL="https://github.com/element-hq/element-x-android.git"
STATE_FILE="$(git rev-parse --git-dir)/larpgram-sync-tag"
# Upstream files we deliberately removed: if upstream changed them, keep them deleted.
DROPPED_PATHS=(.github AGENTS.md CHANGES.md CLAUDE.md CODEOWNERS CONTRIBUTING.md)
# Element's own sign-in screens, removed 2026-10-01 (Larpgram signs in through screens/tg). Only
# directories that are gone entirely are listed. `changeserver` and `screens/onboarding` each keep
# one file, so new upstream files there show up as conflicts to delete by hand.
LOGIN_MAIN="features/login/impl/src/main/kotlin/io/element/android/features/login/impl"
LOGIN_TEST="features/login/impl/src/test/kotlin/io/element/android/features/login/impl"
DROPPED_PATHS+=(
    "$LOGIN_MAIN/LoginFlowTransitionHandler.kt"
    "$LOGIN_MAIN/accountprovider"
    "$LOGIN_MAIN/classic"
    "$LOGIN_MAIN/di"
    "$LOGIN_MAIN/dialogs"
    "$LOGIN_MAIN/error"
    "$LOGIN_MAIN/localnetwork"
    "$LOGIN_MAIN/login"
    "$LOGIN_MAIN/qrcode"
    "$LOGIN_MAIN/util"
    "$LOGIN_MAIN/screens/chooseaccountprovider"
    "$LOGIN_MAIN/screens/classic"
    "$LOGIN_MAIN/screens/confirmaccountprovider"
    "$LOGIN_MAIN/screens/createaccount"
    "$LOGIN_MAIN/screens/loginpassword"
    "$LOGIN_MAIN/screens/qrcode"
    "$LOGIN_TEST/accountprovider"
    "$LOGIN_TEST/changeserver"
    "$LOGIN_TEST/classic"
    "$LOGIN_TEST/di"
    "$LOGIN_TEST/error"
    "$LOGIN_TEST/qrcode"
    "$LOGIN_TEST/screens/chooseaccountprovider"
    "$LOGIN_TEST/screens/classic"
    "$LOGIN_TEST/screens/confirmaccountprovider"
    "$LOGIN_TEST/screens/loginpassword"
    "$LOGIN_TEST/screens/qrcode"
)

ensure_upstream() {
    if ! git remote get-url upstream >/dev/null 2>&1; then
        git remote add upstream "$UPSTREAM_URL"
    fi
    git fetch --quiet --tags upstream
}

# Graft every commit carrying `Upstream-Tag:` onto that tag (in addition to its real parents).
restore_grafts() {
    ensure_upstream
    git --no-replace-objects log --format='%H %(trailers:key=Upstream-Tag,valueonly,separator=%x20)' \
        | while read -r commit tag; do
            [ -n "${tag:-}" ] || continue
            local parents
            parents="$(git --no-replace-objects log -1 --format=%P "$commit")"
            git replace -f --graft "$commit" $parents "$(git rev-parse "$tag^{commit}")"
            echo "graft: $(git log -1 --format='%h %s' "$commit") -> $tag"
        done
}

start() {
    local tag="$1"
    if [ -n "$(git status --porcelain)" ]; then
        echo "Working tree is not clean, commit or stash first." >&2
        exit 1
    fi
    restore_grafts
    echo "merge base: $(git log -1 --format='%h %s' "$(git merge-base HEAD "$tag")")"
    echo "$tag" > "$STATE_FILE"
    if git merge --squash --no-commit "$tag"; then
        echo "Merged without conflicts."
    else
        echo "Conflicts to resolve (then run: $0 finish)."
    fi
    # Keep the upstream files we removed deleted.
    for path in "${DROPPED_PATHS[@]}"; do
        if ! git cat-file -e "HEAD:$path" 2>/dev/null; then
            git rm -r -q --force --ignore-unmatch -- "$path"
            rm -rf -- "$path"
        fi
    done
    git status --short | grep -E '^(UU|AA|DU|UD|AU|UA|DD) ' || true
}

finish() {
    local tag
    tag="$(cat "$STATE_FILE" 2>/dev/null || true)"
    if [ -z "$tag" ]; then
        echo "No sync in progress (run: $0 start <tag>)." >&2
        exit 1
    fi
    if git diff --name-only --diff-filter=U | grep -q .; then
        echo "Unresolved conflicts left:" >&2
        git diff --name-only --diff-filter=U >&2
        exit 1
    fi
    if [ -n "${1:-}" ]; then
        { echo "sync: Element X Android $tag"; echo; cat "$1"; echo; echo "Upstream-Tag: $tag"; } | git commit --quiet -F -
    else
        git commit --quiet -m "sync: Element X Android $tag" -m "Upstream-Tag: $tag"
    fi
    local head
    head="$(git rev-parse HEAD)"
    git replace -f --graft "$head" "$(git rev-parse HEAD~1)" "$(git rev-parse "$tag^{commit}")"
    rm -f "$STATE_FILE"
    echo "Committed $(git log -1 --format='%h %s') and grafted onto $tag."
}

# Fails if any upstream release tag is a real (graft-free) ancestor of HEAD.
check() {
    local bad=0
    for tag in $(git --no-replace-objects log --format='%(trailers:key=Upstream-Tag,valueonly,separator=%x20)' | grep -v '^$' | sort -u); do
        if git --no-replace-objects merge-base --is-ancestor "$tag^{commit}" HEAD 2>/dev/null; then
            echo "Element history leaked: $tag is a real ancestor of HEAD." >&2
            bad=1
        fi
    done
    [ "$bad" = 0 ] && echo "OK: no Element history in HEAD."
    return "$bad"
}

# Element's Kotlin files that we edited, where none of our added lines carries a marker. During a
# sync an unmarked hunk looks like Element's own code and is easy to overwrite (audit C-007).
unmarked() {
    local tag file bad=0
    tag="$(git --no-replace-objects log --format='%(trailers:key=Upstream-Tag,valueonly,separator=%x20)' | grep -v '^$' | head -1)"
    while read -r file; do
        # No `grep -q` straight on the diff: with pipefail its early exit fails the pipeline.
        if ! git diff "$tag" -- "$file" | grep '^+' | grep -v '^+++' | grep -cE 'Правка форка|[Ll]arpgram|форк|Форк' >/dev/null; then
            echo "$file"
            bad=1
        fi
    done < <(git diff --name-only --diff-filter=M "$tag" -- '*.kt' ':!*/src/test/*' ':!*/src/androidTest/*')
    [ "$bad" = 0 ] && echo "OK: every edited Element file has a marker (base $tag)."
    return "$bad"
}

case "${1:-}" in
    restore) restore_grafts ;;
    check) check ;;
    unmarked) unmarked ;;
    start) [ -n "${2:-}" ] || { echo "Usage: $0 start <tag>" >&2; exit 1; }; start "$2" ;;
    finish) finish "${2:-}" ;;
    *) sed -n '2,23p' "$0"; exit 1 ;;
esac
