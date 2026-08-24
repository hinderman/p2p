package com.project.backend.infrastructure.notification;

import com.project.backend.application.dto.InvitationEmailMessage;
import com.project.backend.application.port.out.InvitationEmailDeliveryPort;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** SMTP implementation; local Mailpit and Brevo use the same adapter and configuration contract. */
@Component
public final class SmtpInvitationEmailDeliveryAdapter implements InvitationEmailDeliveryPort {
    private final JavaMailSender mailSender;
    private final InvitationEmailProperties properties;
    private final InvitationLinkBuilder links;

    public SmtpInvitationEmailDeliveryAdapter(JavaMailSender mailSender, InvitationEmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.links = new InvitationLinkBuilder(properties);
    }

    @Override
    public void deliver(InvitationEmailMessage invitation) {
        String link = links.build(invitation.rawToken());
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(properties.from(), properties.senderName(), StandardCharsets.UTF_8.name()));
            helper.setTo(invitation.recipientEmail().value());
            helper.setSubject("Invitacion para revisar y aceptar un prestamo");
            helper.setText(textBody(link), htmlBody(link));
            message.setHeader("X-Project-Invitation-Id", invitation.invitationId().value().toString());
            mailSender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to deliver invitation email", exception);
        }
    }

    private static String textBody(String link) {
        return "Has recibido una invitacion para revisar y aceptar un prestamo.\n\n"
                + "Abre este enlace en el dispositivo donde usaras la aplicacion:\n" + link + "\n\n"
                + "El enlace es personal, de un solo uso y vence en siete dias.";
    }

    private static String htmlBody(String link) {
        return "<p>Has recibido una invitacion para revisar y aceptar un prestamo.</p>"
                + "<p><a href=\"" + link + "\">Revisar invitacion</a></p>"
                + "<p>El enlace es personal, de un solo uso y vence en siete dias.</p>";
    }
}
