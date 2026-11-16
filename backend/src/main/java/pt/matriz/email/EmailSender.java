package pt.matriz.email;

/**
 * Email adapter (docs/architecture.md section 5): the only email Matriz sends is the password reset
 * link (FR1c). Behind an interface so the provider can change.
 */
public interface EmailSender {

    /** Sends a plain-text email. Throws on failure; the caller decides what to do (D36). */
    void send(String to, String subject, String body);
}
