package com.nemal.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;
    private final String fromAddress;
    private final String frontendUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${notification.email.enabled:true}") boolean emailEnabled,
            @Value("${spring.mail.username:}") String fromAddress,
            @Value("${app.frontend.url:}") String frontendUrl
    ) {
        this.mailSender = mailSender;
        this.emailEnabled = emailEnabled;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    @Async
    public void sendNotificationEmail(String toEmail, String toName, String subject, String message) {
        if (!emailEnabled) {
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(buildHtmlBody(toName, subject, message), true);
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }

            mailSender.send(mimeMessage);
        } catch (MessagingException | MailException ex) {
            logger.warn("Failed to send notification email to {}: {}", toEmail, ex.getMessage());
        }
    }

    private String buildHtmlBody(String toName, String subject, String message) {
        String greetingName = (toName != null && !toName.isBlank()) ? toName : "there";
        String ctaButton = (frontendUrl != null && !frontendUrl.isBlank())
                ? String.format(
                        "<tr><td style=\"padding: 24px 32px 8px 32px;\">"
                                + "<a href=\"%s\" style=\"display:inline-block;background-color:#4f46e5;color:#ffffff;"
                                + "text-decoration:none;padding:10px 20px;border-radius:6px;font-size:14px;font-weight:600;\">"
                                + "View in Mitra Interview Scheduler</a></td></tr>",
                        frontendUrl
                )
                : "";

        return "<!DOCTYPE html>"
                + "<html><body style=\"margin:0;padding:0;background-color:#f4f4f7;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f4f7;padding:32px 0;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background-color:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;\">"
                + "<tr><td style=\"background-color:#4f46e5;padding:20px 32px;\">"
                + "<span style=\"color:#ffffff;font-size:18px;font-weight:700;\">Mitra Interview Scheduler</span></td></tr>"
                + "<tr><td style=\"padding:32px 32px 8px 32px;\">"
                + "<p style=\"margin:0 0 12px 0;font-size:14px;color:#111827;\">Hi " + escapeHtml(greetingName) + ",</p>"
                + "<h2 style=\"margin:0 0 12px 0;font-size:18px;color:#111827;\">" + escapeHtml(subject) + "</h2>"
                + "<p style=\"margin:0;font-size:14px;line-height:1.6;color:#374151;\">" + escapeHtml(message) + "</p>"
                + "</td></tr>"
                + ctaButton
                + "<tr><td style=\"padding:24px 32px 32px 32px;border-top:1px solid #e5e7eb;margin-top:16px;\">"
                + "<p style=\"margin:16px 0 0 0;font-size:12px;color:#9ca3af;\">"
                + "You're receiving this because you have an account on Mitra Interview Scheduler."
                + "You can manage your email notification preferences in Settings.</p>"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
