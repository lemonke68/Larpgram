// Письма с кодом через matrix-exim-relay — тот же релей, что использует MAS.
//
// Контейнер подключён к сети matrix-exim-relay, поэтому хост резолвится по имени. Правки конфига
// релея не нужны: он уже принимает почту от контейнеров своей сети.

import nodemailer from 'nodemailer';

const FROM = process.env.MAIL_FROM || 'Larpgram <noreply@mango-kokos.ru>';

const TEXTS = {
  register: (code) => ({
    subject: `Код регистрации Larpgram: ${code}`,
    text:
      `Ваш код для регистрации в Larpgram: ${code}\n\n` +
      `Код действует 10 минут. Введите его в приложении.\n\n` +
      `Если вы не регистрировались в Larpgram, просто проигнорируйте это письмо.`,
  }),
  reset: (code) => ({
    subject: `Код для сброса пароля Larpgram: ${code}`,
    text:
      `Ваш код для сброса пароля в Larpgram: ${code}\n\n` +
      `Код действует 10 минут. Введите его в приложении и задайте новый пароль.\n\n` +
      `Если вы не просили сбросить пароль, просто проигнорируйте это письмо — пароль останется прежним.`,
  }),
  email: (code) => ({
    subject: `Код подтверждения почты Larpgram: ${code}`,
    text:
      `Ваш код для привязки этой почты к аккаунту Larpgram: ${code}\n\n` +
      `Код действует 10 минут. Введите его в приложении.\n\n` +
      `Если вы не привязывали почту в Larpgram, просто проигнорируйте это письмо.`,
  }),
};

export function createMailer() {
  const transport = nodemailer.createTransport({
    host: process.env.SMTP_HOST || 'matrix-exim-relay',
    port: Number(process.env.SMTP_PORT || 8025),
    secure: false,
    // Внутренний релей без STARTTLS/сертификата — доставка идёт по докер-сети.
    ignoreTLS: true,
  });

  return {
    /** kind: 'register' | 'reset' | 'email'. */
    async sendCode(kind, to, code) {
      await transport.sendMail({ from: FROM, to, ...TEXTS[kind](code) });
    },
  };
}
