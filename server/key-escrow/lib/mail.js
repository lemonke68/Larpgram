// Отправка кода на почту через matrix-exim-relay — тот же релей, что использует MAS.
//
// Контейнер escrow подключён к сети matrix-exim-relay, поэтому хост резолвится по имени.
// Правки конфига релея не нужны: он уже принимает почту от контейнеров своей сети.

import nodemailer from 'nodemailer';

const transport = nodemailer.createTransport({
  host: process.env.SMTP_HOST || 'matrix-exim-relay',
  port: Number(process.env.SMTP_PORT || 8025),
  secure: false,
  // Внутренний релей без STARTTLS/сертификата — доставка идёт по докер-сети.
  ignoreTLS: true,
});

const FROM = process.env.MAIL_FROM || 'Larpgram <no-reply@mango-kokos.ru>';

export async function sendCode(to, code) {
  await transport.sendMail({
    from: FROM,
    to,
    subject: `Код входа Larpgram: ${code}`,
    text:
      `Ваш код для входа на новом устройстве: ${code}\n\n` +
      `Код действует 10 минут. Введите его в приложении на экране подтверждения.\n\n` +
      `Если вы не входили в Larpgram, просто проигнорируйте это письмо — доступ никто не получит.`,
  });
}
