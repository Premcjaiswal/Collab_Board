package com.collabboard.support;

import com.collabboard.config.AppProperties;
import com.collabboard.notification.EmailService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test double that records outgoing emails instead of sending them. */
public class RecordingEmailService extends EmailService {

    public record Sent(String to, String subject, String body) {
    }

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");
    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    public RecordingEmailService(ObjectProvider<JavaMailSender> mailSender, AppProperties props) {
        super(mailSender, props);
    }

    @Override
    public boolean send(String to, String subject, String body, String devLogLine) {
        sent.add(new Sent(to, subject, body));
        return true;
    }

    public String lastCodeFor(String email) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            Sent s = sent.get(i);
            if (s.to().equalsIgnoreCase(email)) {
                Matcher m = CODE.matcher(s.body());
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        throw new AssertionError("No code emailed to " + email);
    }

    public List<Sent> sent() {
        return sent;
    }
}
