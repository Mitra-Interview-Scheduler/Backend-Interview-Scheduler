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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        ParsedMessage parsed = parseMessage(message);

        StringBuilder introHtml = new StringBuilder();
        for (String paragraph : parsed.introParagraphs()) {
            introHtml.append("<p style=\"margin:0 0 12px 0;font-size:15px;line-height:1.6;color:#374151;\">")
                    .append(escapeHtml(paragraph))
                    .append("</p>");
        }

        String detailsHtml = "";
        if (!parsed.details().isEmpty()) {
            StringBuilder rows = new StringBuilder();
            int index = 0;
            for (Map.Entry<String, String> entry : parsed.details().entrySet()) {
                boolean last = index == parsed.details().size() - 1;
                String border = last ? "" : "border-bottom:1px solid #eef2ff;";
                rows.append("<tr>")
                        .append("<td style=\"padding:12px 16px;width:34%;vertical-align:top;")
                        .append(border)
                        .append("\">")
                        .append("<span style=\"display:block;font-size:11px;font-weight:700;letter-spacing:0.04em;")
                        .append("text-transform:uppercase;color:#6b7280;\">")
                        .append(escapeHtml(entry.getKey()))
                        .append("</span></td>")
                        .append("<td style=\"padding:12px 16px;vertical-align:top;")
                        .append(border)
                        .append("\">")
                        .append("<span style=\"display:block;font-size:15px;font-weight:600;color:#111827;line-height:1.4;\">")
                        .append(escapeHtml(entry.getValue()))
                        .append("</span></td>")
                        .append("</tr>");
                index++;
            }
            detailsHtml = "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                    + "style=\"margin:8px 0 8px 0;background-color:#f8fafc;border:1px solid #e5e7eb;"
                    + "border-radius:10px;overflow:hidden;\">"
                    + rows
                    + "</table>";
        }

        String ctaButton = (frontendUrl != null && !frontendUrl.isBlank())
                ? String.format(
                        "<tr><td style=\"padding:8px 32px 8px 32px;\">"
                                + "<a href=\"%s\" style=\"display:inline-block;background-color:#4f46e5;color:#ffffff;"
                                + "text-decoration:none;padding:12px 22px;border-radius:8px;font-size:14px;font-weight:600;\">"
                                + "Open Mitra Interview Scheduler</a></td></tr>",
                        frontendUrl
                )
                : "";

        return "<!DOCTYPE html>"
                + "<html><body style=\"margin:0;padding:0;background-color:#f4f4f7;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f4f7;padding:32px 0;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"520\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background-color:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;\">"
                + "<tr><td style=\"background-color:#4f46e5;padding:22px 32px;\">"
                + "<span style=\"color:#ffffff;font-size:18px;font-weight:700;\">Mitra Interview Scheduler</span></td></tr>"
                + "<tr><td style=\"padding:28px 32px 8px 32px;\">"
                + "<p style=\"margin:0 0 16px 0;font-size:15px;color:#111827;\">Hi " + escapeHtml(greetingName) + ",</p>"
                + "<h2 style=\"margin:0 0 16px 0;font-size:20px;line-height:1.3;color:#111827;\">"
                + escapeHtml(subject) + "</h2>"
                + introHtml
                + detailsHtml
                + "</td></tr>"
                + ctaButton
                + "<tr><td style=\"padding:20px 32px 28px 32px;\">"
                + "<p style=\"margin:0;padding-top:16px;border-top:1px solid #e5e7eb;font-size:12px;line-height:1.5;color:#9ca3af;\">"
                + "You're receiving this because you have an account on Mitra Interview Scheduler. "
                + "You can manage email preferences in Settings.</p>"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    /**
     * Splits notification copy into intro prose and labeled detail rows
     * (e.g. {@code Candidate: Ahamed Rizlan}) for richer email layout.
     */
    private ParsedMessage parseMessage(String message) {
        List<String> intro = new ArrayList<>();
        Map<String, String> details = new LinkedHashMap<>();
        if (message == null || message.isBlank()) {
            return new ParsedMessage(intro, details);
        }

        StringBuilder currentIntro = new StringBuilder();
        for (String rawLine : message.replace("\r\n", "\n").split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                flushIntro(currentIntro, intro);
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0 && colon < line.length() - 1) {
                String label = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (isDetailLabel(label) && !value.isEmpty()) {
                    flushIntro(currentIntro, intro);
                    details.put(label, value);
                    continue;
                }
            }
            if (!currentIntro.isEmpty()) {
                currentIntro.append(' ');
            }
            currentIntro.append(line);
        }
        flushIntro(currentIntro, intro);

        // Fallback: entire message as one intro paragraph when no structured rows.
        if (intro.isEmpty() && details.isEmpty() && !message.isBlank()) {
            intro.add(message.trim());
        }
        return new ParsedMessage(intro, details);
    }

    private static void flushIntro(StringBuilder currentIntro, List<String> intro) {
        if (!currentIntro.isEmpty()) {
            intro.add(currentIntro.toString().trim());
            currentIntro.setLength(0);
        }
    }

    private static boolean isDetailLabel(String label) {
        if (label == null || label.isBlank() || label.length() > 40) {
            return false;
        }
        String normalized = label.trim().toLowerCase();
        if (normalized.equals("candidate")
                || normalized.equals("when")
                || normalized.equals("position")
                || normalized.equals("interviewer")
                || normalized.equals("reason")
                || normalized.equals("preferred time")
                || normalized.equals("interview type")) {
            return true;
        }
        // Avoid treating normal sentences as labels ("Reminder: You have...").
        return !label.contains(" ");
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

    private record ParsedMessage(List<String> introParagraphs, Map<String, String> details) {}
}
