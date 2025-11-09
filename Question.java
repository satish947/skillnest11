// Question.java
import java.util.Arrays;

class Question {
    String subject;
    String question;
    String[] options;
    int correctIndex;
    String explanation;

    Question(String subject, String question, String[] options, int correctIndex) {
        this(subject, question, options, correctIndex, "");
    }
    Question(String subject, String question, String[] options, int correctIndex, String explanation) {
        this.subject = subject;
        this.question = question;
        this.options = Arrays.copyOf(options, 4);
        this.correctIndex = correctIndex;
        this.explanation = explanation == null ? "" : explanation;
    }
}
