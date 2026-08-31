package com.project.backend.infrastructure.notification;

import com.project.backend.application.dto.OutboundEmailKind;
import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.application.port.out.OutboundEmailDeliveryPort;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** SMTP implementation; local Mailpit and Brevo use the same adapter and configuration contract. */
@Component
public final class SmtpOutboundEmailDeliveryAdapter implements OutboundEmailDeliveryPort {
    private final JavaMailSender mailSender;
    private final OutboundEmailProperties properties;
    private final EmailLinkBuilder links;

    public SmtpOutboundEmailDeliveryAdapter(JavaMailSender mailSender, OutboundEmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.links = new EmailLinkBuilder(properties);
    }

    @Override
    public void deliver(OutboundEmailMessage message) {
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(properties.from(), properties.senderName(), StandardCharsets.UTF_8.name()));
            helper.setTo(message.recipientEmail().value());
            helper.setSubject(subject(message.kind()));
            helper.setText(textBody(message), htmlBody(message));
            mimeMessage.setHeader("X-Project-Message-Reference", message.referenceId().toString());
            mailSender.send(mimeMessage);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to deliver %s email".formatted(message.kind()), exception);
        }
    }

    private static String subject(OutboundEmailKind kind) {
        return switch (kind) {
            case LOAN_INVITATION -> "Invitacion para revisar y aceptar un prestamo";
            case ACCOUNT_EMAIL_VERIFICATION -> "Confirma tu correo para activar tu cuenta";
            case EXISTING_ACCOUNT_NOTICE -> "Ya existe una cuenta con este correo";
        };
    }

    private String textBody(OutboundEmailMessage message) {
        return switch (message.kind()) {
            case LOAN_INVITATION -> """
                    Has recibido una invitacion para revisar y aceptar un prestamo.

                    Abre este enlace en el dispositivo donde usaras la aplicacion:
                    %s

                    El enlace es personal, de un solo uso y vence en siete dias."""
                    .formatted(links.build(message.kind(), message.rawToken()));
            case ACCOUNT_EMAIL_VERIFICATION -> """
                    Confirma este correo para activar tu cuenta:
                    %s

                    El enlace es de un solo uso y vence en 24 horas.
                    Si no creaste esta cuenta, ignora este mensaje: sin confirmar, no se activa."""
                    .formatted(links.build(message.kind(), message.rawToken()));
            case EXISTING_ACCOUNT_NOTICE -> """
                    Alguien intento registrar una cuenta con este correo, que ya tiene una.

                    Si fuiste tu, inicia sesion normalmente:
                    %s

                    No se creo ninguna cuenta nueva y tu contrasena no cambio."""
                    .formatted(links.signInUrl());
        };
    }

    private String htmlBody(OutboundEmailMessage message) {
        return switch (message.kind()) {
            case LOAN_INVITATION -> "<p>Has recibido una invitacion para revisar y aceptar un prestamo.</p>"
                    + "<p><a href=\"" + links.build(message.kind(), message.rawToken()) + "\">Revisar invitacion</a></p>"
                    + "<p>El enlace es personal, de un solo uso y vence en siete dias.</p>";
            case ACCOUNT_EMAIL_VERIFICATION -> "<p>Confirma este correo para activar tu cuenta.</p>"
                    + "<p><a href=\"" + links.build(message.kind(), message.rawToken()) + "\">Confirmar mi correo</a></p>"
                    + "<p>El enlace es de un solo uso y vence en 24 horas. "
                    + "Si no creaste esta cuenta, ignora este mensaje: sin confirmar, no se activa.</p>";
            case EXISTING_ACCOUNT_NOTICE -> "<p>Alguien intento registrar una cuenta con este correo, que ya tiene una.</p>"
                    + "<p>Si fuiste tu, <a href=\"" + links.signInUrl() + "\">inicia sesion</a> normalmente.</p>"
                    + "<p>No se creo ninguna cuenta nueva y tu contrasena no cambio.</p>";
        };
    }
}
