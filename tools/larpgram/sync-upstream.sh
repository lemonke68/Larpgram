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
#
# Never `git commit --amend` a sync commit: with the grafts active, amend copies the grafted upstream
# parent into the real commit and the next push would upload Element's whole history. Use `finish msg`.
#
set -euo pipefail

UPSTREAM_URL="https://github.com/element-hq/element-x-android.git"
STATE_FILE="$(git rev-parse --git-dir)/larpgram-sync-tag"
# Upstream files we deliberately removed: if upstream changed them, keep them deleted.
DROPPED_PATHS=(.github AGENTS.md CHANGES.md CLAUDE.md CODEOWNERS CONTRIBUTING.md)

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

case "${1:-}" in
    restore) restore_grafts ;;
    check) check ;;
    start) [ -n "${2:-}" ] || { echo "Usage: $0 start <tag>" >&2; exit 1; }; start "$2" ;;
    finish) finish "${2:-}" ;;
    *) sed -n '2,22p' "$0"; exit 1 ;;
esac
