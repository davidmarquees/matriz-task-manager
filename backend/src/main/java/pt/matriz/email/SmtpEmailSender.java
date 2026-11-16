package pt.matriz.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP implementation; in development the server is Mailpit (docker-compose.yml), so nothing leaves the machine. */
@Component
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mail;
    private final String from;

    SmtpEmailSender(JavaMailSender mail, @Value("${matriz.mail-from}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mail.send(message);
    }
}
