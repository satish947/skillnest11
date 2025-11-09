// SkillNestStudentQuiz.java
// Main application — keep this as the public class in SkillNestStudentQuiz.java

import javax.imageio.ImageIO;
import javax.print.DocFlavor;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import java.util.stream.Collectors;

public class SkillNestStudentQuiz extends JFrame {
    // Config
    private static final int TIME_PER_QUESTION = 60; // seconds
    private static final String QUESTIONS_FILE = "questions.json";
    private static final String SCORES_FILE = System.getProperty("user.home") + File.separator + "skillnest_scores.csv";
    private static final String VERSIONS_FILE = System.getProperty("user.home") + File.separator + ".skillnest_qversions";

    // Layout
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);

    // Start Screen components
    private JTextField rollField = new JTextField(16);
    private JTextField nameField = new JTextField(16);
    private JTextField classField = new JTextField(16);
    private JComboBox<String> subjectBox;

    // Quiz screen
    private JLabel questionLabel;
    private JRadioButton[] options = new JRadioButton[4];
    private ButtonGroup group = new ButtonGroup();
    private JButton nextButton, backButton, quitButton;
    private JProgressBar progressBar;
    private JLabel timerLabel, scoreLabel;

    // Timer and data
    private javax.swing.Timer timer;
    private int timeLeft;
    private List<Question> questions = new ArrayList<>();
    private int currentQuestion = 0;
    private int score = 0;
    private Integer[] selectedDisplayedIndex;
    private List<int[]> displayedMappings = new ArrayList<>();

    // Student info
    private String roll, name, cls, subject;

    // Extras
    private JButton analyticsBtn, reviewBtn, exportBtn, explanationsBtn, exportExplanationsBtn;
    private Map<String, String> questionVersions = new HashMap<>(); // qid->hash (this session)
    private Map<String, String> loadedQuestionHashes = new HashMap<>(); // persisted versions
    private int totalCorrectCount = 0;
    private int totalIncorrectCount = 0;

    public SkillNestStudentQuiz() {
        setTitle("SkillNest — Smart Student Quiz");
        setSize(980, 680);
        setMinimumSize(new Dimension(880, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        loadPersistedQuestionVersions();

        // show login/register dialog before allowing access
        LoginRegisterDialog lr = new LoginRegisterDialog(this);
        String user = lr.showAndReturnUser();
        if (user == null) {
            // user cancelled login -- exit application
            System.exit(0);
        }
        // optionally prefill name based on username
        nameField.setText(user);

        mainPanel.add(buildStartPanel(), "start");
        mainPanel.add(buildQuizPanel(), "quiz");
        add(mainPanel, BorderLayout.CENTER);
        add(buildHeader(), BorderLayout.NORTH);

        cardLayout.show(mainPanel, "start");
    }

    // Header panel
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(28, 93, 140));
        header.setBorder(new EmptyBorder(12, 18, 12, 18));

        JLabel title = new JLabel("🧠 SkillNest", SwingConstants.LEFT);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));

        JLabel subtitle = new JLabel("Smart Student Quiz", SwingConstants.RIGHT);
        subtitle.setForeground(new Color(210, 230, 255));
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        header.add(title, BorderLayout.WEST);
        header.add(subtitle, BorderLayout.EAST);
        return header;
    }

    // Start panel (polished)
    private JPanel buildStartPanel() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(new Color(245, 248, 252));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 12, 12, 12);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(new EmptyBorder(18, 18, 18, 18), new DropShadowBorder()));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 10, 10, 10);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel h = new JLabel("Start Quiz", SwingConstants.CENTER);
        h.setFont(new Font("Segoe UI", Font.BOLD, 20));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        card.add(h, c);

        c.gridwidth = 1;
        c.gridy = 1; c.gridx = 0;
        card.add(new JLabel("Roll No:"), c);
        c.gridx = 1;
        styleTextField(rollField);
        card.add(rollField, c);

        c.gridy = 2; c.gridx = 0;
        card.add(new JLabel("Name:"), c);
        c.gridx = 1;
        styleTextField(nameField);
        card.add(nameField, c);

        c.gridy = 3; c.gridx = 0;
        card.add(new JLabel("Class/Section:"), c);
        c.gridx = 1;
        styleTextField(classField);
        card.add(classField, c);

        c.gridy = 4; c.gridx = 0;
        card.add(new JLabel("Select Subject:"), c);
        c.gridx = 1;
        subjectBox = new JComboBox<>(new String[]{"Physics", "Chemistry", "Biology"});
        subjectBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        card.add(subjectBox, c);

        // Move focus forward when Enter is pressed: Roll -> Name -> Class -> Subject
        rollField.addActionListener(e -> nameField.requestFocusInWindow());
        nameField.addActionListener(e -> classField.requestFocusInWindow());
        classField.addActionListener(e -> subjectBox.requestFocusInWindow());

        // buttons row
        c.gridy = 5; c.gridx = 0; c.gridwidth = 2;
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 8));
        btnRow.setOpaque(false);

        JButton startButton = createPrimaryButton("Start Quiz");
        startButton.addActionListener(e -> startQuiz());
        btnRow.add(startButton);

        JButton viewBtn = createSecondaryButton("📋 View/Delete Scores");
        viewBtn.addActionListener(e -> viewAndManageScores());
        btnRow.add(viewBtn);

        JButton quitBtn = createSecondaryButton("Quit");
        quitBtn.addActionListener(e -> System.exit(0));
        btnRow.add(quitBtn);

        card.add(btnRow, c);

        // set Enter behavior: default button triggers Start
        getRootPane().setDefaultButton(startButton);

        gbc.gridx = 0; gbc.gridy = 0;
        root.add(card, gbc);

        return root;
    }

    private void styleTextField(JTextField tf) {
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tf.setBorder(new CompoundBorder(new LineBorder(new Color(200, 210, 220)), new EmptyBorder(6,8,6,8)));
        tf.addActionListener(e -> {}); // avoid unwanted default actions
    }

    // Quiz panel (polished)
    private JPanel buildQuizPanel() {
        JPanel quizPanel = new JPanel(new BorderLayout(12, 12));
        quizPanel.setBackground(new Color(240, 246, 250));
        quizPanel.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel questionCard = new JPanel(new BorderLayout(8,8));
        questionCard.setBackground(Color.WHITE);
        questionCard.setBorder(new CompoundBorder(new EmptyBorder(12,12,12,12), new LineBorder(new Color(220,225,230))));

        questionLabel = new JLabel("Question", SwingConstants.CENTER);
        questionLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        questionLabel.setBorder(new EmptyBorder(8,8,8,8));
        questionCard.add(questionLabel, BorderLayout.NORTH);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new GridLayout(4, 1, 10, 10));
        optionsPanel.setBackground(Color.WHITE);
        optionsPanel.setBorder(new EmptyBorder(6,6,6,6));

        for (int i = 0; i < 4; i++) {
            JRadioButton rb = new JRadioButton("Option " + (i+1));
            rb.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            rb.setOpaque(true);
            rb.setBackground(new Color(250, 251, 253));
            rb.setBorder(new CompoundBorder(new LineBorder(new Color(220,225,230)), new EmptyBorder(8,10,8,10)));
            rb.setFocusPainted(false);
            rb.setActionCommand(String.valueOf(i));
            group.add(rb);
            optionsPanel.add(rb);
            options[i] = rb;

            rb.addActionListener(e -> {
                nextButton.setEnabled(true);
                selectedDisplayedIndex[currentQuestion] = Integer.parseInt(e.getActionCommand());
                evaluateCurrentSelection();
            });
        }

        questionCard.add(optionsPanel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(10,10));
        footer.setBackground(Color.WHITE);
        footer.setBorder(new EmptyBorder(10,0,0,0));

        JPanel leftInfo = new JPanel(new GridLayout(2,1));
        leftInfo.setOpaque(false);
        timerLabel = new JLabel("⏱ Time: 0s");
        timerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        leftInfo.add(timerLabel);
        leftInfo.add(scoreLabel);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(200, 22));
        progressBar.setBorder(new LineBorder(new Color(210,215,220)));

        footer.add(leftInfo, BorderLayout.WEST);
        footer.add(progressBar, BorderLayout.CENTER);

        questionCard.add(footer, BorderLayout.SOUTH);

        quizPanel.add(questionCard, BorderLayout.CENTER);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
        nav.setBackground(new Color(240,246,250));
        backButton = createSecondaryButton("⬅ Back");
        backButton.addActionListener(e -> previousQuestion());
        nav.add(backButton);

        nextButton = createPrimaryButton("Next ➡");
        nextButton.setEnabled(false);
        nextButton.addActionListener(e -> nextQuestion());
        nav.add(nextButton);

        quitButton = createSecondaryButton("Quit Quiz ❌");
        quitButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Quit the quiz? Your progress will be lost.", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                stopTimer();
                cardLayout.show(mainPanel, "start");
            }
        });
        nav.add(quitButton);

        // Analytics / Review / Export / Explanations Buttons
        analyticsBtn = createSecondaryButton("📊 Analytics");
        analyticsBtn.addActionListener(e -> showAnalytics());
        analyticsBtn.setEnabled(false); // only enable after quiz
        nav.add(analyticsBtn);

        reviewBtn = createSecondaryButton("🔁 Review Answers");
        reviewBtn.addActionListener(e -> openReviewWindow());
        reviewBtn.setEnabled(false);
        nav.add(reviewBtn);

        exportBtn = createSecondaryButton("📄 Export Review");
        exportBtn.addActionListener(e -> exportReviewAsPDF());
        exportBtn.setEnabled(false);
        nav.add(exportBtn);

        explanationsBtn = createSecondaryButton("📝 Explanations");
        explanationsBtn.addActionListener(e -> showAllExplanations());
        explanationsBtn.setEnabled(false);
        nav.add(explanationsBtn);

        exportExplanationsBtn = createSecondaryButton("📥 Export Explanations PDF");
        exportExplanationsBtn.addActionListener(e -> exportExplanationsAsPDF());
        exportExplanationsBtn.setEnabled(false);
        nav.add(exportExplanationsBtn);

        quizPanel.add(nav, BorderLayout.SOUTH);

        // keyboard navigation
        quizPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
        quizPanel.getActionMap().put("next", new AbstractAction(){ public void actionPerformed(ActionEvent e) { if (nextButton.isEnabled()) nextButton.doClick(); }});
        quizPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prev");
        quizPanel.getActionMap().put("prev", new AbstractAction(){ public void actionPerformed(ActionEvent e) { backButton.doClick(); }});

        return quizPanel;
    }

    // Rounded primary / secondary buttons
    private JButton createPrimaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setFocusable(false);
        b.setBackground(new Color(30,130,200));
        b.setForeground(Color.WHITE);
        b.setBorder(new RoundedBorder(8, new Color(25,110,170)));
        b.setPreferredSize(new Dimension(150, 36));
        return b;
    }
    private JButton createSecondaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        b.setFocusable(false);
        b.setBackground(Color.WHITE);
        b.setForeground(new Color(35,35,35));
        b.setBorder(new RoundedBorder(8, new Color(210,215,220)));
        b.setPreferredSize(new Dimension(170, 36));
        return b;
    }

    // Start quiz
    private void startQuiz() {
        roll = rollField.getText().trim();
        name = nameField.getText().trim();
        cls = classField.getText().trim();
        subject = ((String)subjectBox.getSelectedItem()).trim();

        if (roll.isEmpty() || name.isEmpty() || cls.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all details.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<Question> all = loadQuestionsFromJsonSimple();
        List<Question> filtered = all.stream().filter(q -> q.subject.equalsIgnoreCase(subject)).collect(Collectors.toList());
        if (filtered.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No questions found for " + subject + ". Please check " + QUESTIONS_FILE, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // shuffle & initialize
        Collections.shuffle(filtered);
        questions = filtered;
        selectedDisplayedIndex = new Integer[questions.size()];
        displayedMappings = new ArrayList<>(Collections.nCopies(questions.size(), null));

        // initialize version hashes for this session
        questionVersions.clear();
        for (Question q : questions) questionVersions.put(qId(q), computeQuestionHash(q));

        currentQuestion = 0;
        score = 0;
        totalCorrectCount = 0;
        totalIncorrectCount = 0;
        updateScoreLabel();

        // ensure analytics/review/export/explanations disabled during quiz
        analyticsBtn.setEnabled(false);
        reviewBtn.setEnabled(false);
        exportBtn.setEnabled(false);
        explanationsBtn.setEnabled(false);
        exportExplanationsBtn.setEnabled(false);

        cardLayout.show(mainPanel, "quiz");
        showQuestion();
    }

    // ===== Simple JSON parser (same as earlier) =====
    private List<Question> loadQuestionsFromJsonSimple() {
        List<Question> list = new ArrayList<>();
        Path path = Paths.get(QUESTIONS_FILE);
        if (Files.notExists(path)) {
            JOptionPane.showMessageDialog(this, QUESTIONS_FILE + " not found. Please create it in working directory.", "File Missing", JOptionPane.ERROR_MESSAGE);
            return list;
        }
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            String compact = json.replace("\r", " ").replace("\n", " ");

            Pattern objPattern = Pattern.compile("\\{(.*?)\\}");
            Matcher objMatcher = objPattern.matcher(compact);
            while (objMatcher.find()) {
                String obj = objMatcher.group(1);
                String subj = extractStringField(obj, "subject");
                String qtext = extractStringField(obj, "question");
                String ans = extractStringField(obj, "answer");
                String[] opts = extractStringArray(obj, "options");
                String expl = extractStringField(obj, "explanation"); // optional
                if (subj == null || qtext == null || ans == null || opts == null || opts.length != 4) {
                    System.err.println("Skipping malformed question object: " + obj);
                    continue;
                }
                int correctIndex = 0;
                for (int i = 0; i < 4; i++) if (opts[i].equals(ans)) correctIndex = i;
                list.add(new Question(subj, qtext, opts, correctIndex, expl == null ? "" : expl));
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error reading " + QUESTIONS_FILE + ": " + e.getMessage(), "I/O Error", JOptionPane.ERROR_MESSAGE);
        }
        return list;
    }

    private String extractStringField(String obj, String fieldName) {
        String pattern = "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(obj);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private String[] extractStringArray(String obj, String fieldName) {
        String pattern = "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\\[(.*?)\\]";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(obj);
        if (m.find()) {
            String inside = m.group(1);
            List<String> items = new ArrayList<>();
            Pattern itemPat = Pattern.compile("\"([^\"]*)\"");
            Matcher im = itemPat.matcher(inside);
            while (im.find()) items.add(im.group(1));
            return items.toArray(new String[0]);
        }
        return null;
    }

    // Show question
    private void showQuestion() {
        if (currentQuestion < 0 || currentQuestion >= questions.size()) return;
        Question q = questions.get(currentQuestion);
        questionLabel.setText("<html><div style='text-align:center;'>" + (currentQuestion+1) + ". " + escapeHtml(q.question) + "</div></html>");

        int[] mapping = new int[]{0,1,2,3};
        List<Integer> mapList = new ArrayList<>();
        for (int i = 0; i < 4; i++) mapList.add(i);
        Collections.shuffle(mapList);
        for (int i = 0; i < 4; i++) mapping[i] = mapList.get(i);
        displayedMappings.set(currentQuestion, mapping);

        group.clearSelection();
        for (int i = 0; i < 4; i++) {
            options[i].setEnabled(true);
            options[i].setText(q.options[mapping[i]]);
            options[i].setActionCommand(String.valueOf(i));
        }

        Integer prev = selectedDisplayedIndex[currentQuestion];
        if (prev != null) {
            if (prev >=0 && prev < 4) options[prev].setSelected(true);
            nextButton.setEnabled(true);
        } else nextButton.setEnabled(false);

        backButton.setEnabled(currentQuestion > 0);
        int progress = (int)Math.round(((currentQuestion+1) * 100.0) / questions.size());
        progressBar.setValue(progress);
        progressBar.setString("Progress: " + (currentQuestion+1) + " / " + questions.size());

        startTimerForQuestion();
    }

    private String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    // Timer
    private void startTimerForQuestion() {
        stopTimer();
        timeLeft = TIME_PER_QUESTION;
        timerLabel.setText("⏱ Time: " + timeLeft + "s");
        timer = new javax.swing.Timer(1000, e -> {
            timeLeft--;
            timerLabel.setText("⏱ Time: " + timeLeft + "s");
            if (timeLeft <= 0) {
                ((javax.swing.Timer)e.getSource()).stop();
                // treat as skip/no selection and move on (no change in selection)
                nextQuestion();
            }
        });
        timer.setInitialDelay(1000);
        timer.start();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    // Navigation
    private void nextQuestion() {
        // No immediate feedback. Just move forward after recording selection (selection recorded by radio button handler).
        evaluateCurrentSelection(); // updates score label
        if (currentQuestion < questions.size() - 1) {
            currentQuestion++;
            showQuestion();
        } else {
            stopTimer();
            endQuiz();
        }
    }

    private void previousQuestion() {
        if (currentQuestion > 0) {
            currentQuestion--;
            showQuestion();
            evaluateCurrentSelection();
        } else {
            JOptionPane.showMessageDialog(this, "You're on the first question!");
        }
    }

    private void evaluateCurrentSelection() {
        int s = 0;
        for (int qi = 0; qi < questions.size(); qi++) {
            Integer selDisplayed = selectedDisplayedIndex[qi];
            if (selDisplayed == null) continue;
            int[] mapping = displayedMappings.get(qi);
            if (mapping == null) continue;
            int originalIndex = mapping[selDisplayed];
            if (originalIndex == questions.get(qi).correctIndex) s++;
        }
        score = s;
        updateScoreLabel();
    }

    private void updateScoreLabel() { scoreLabel.setText("Score: " + score); }

    // End quiz
    private void endQuiz() {
        int total = questions.size();
        // recompute final correct/incorrect counts
        int corrects = 0;
        int incorrects = 0;
        for (int i = 0; i < questions.size(); i++) {
            Integer sel = selectedDisplayedIndex[i];
            if (sel == null) { incorrects++; continue; }
            int[] mapping = displayedMappings.get(i);
            if (mapping == null) { incorrects++; continue; }
            int original = mapping[sel];
            if (original == questions.get(i).correctIndex) corrects++; else incorrects++;
        }
        totalCorrectCount = corrects;
        totalIncorrectCount = incorrects;
        score = corrects;
        int percent = (total == 0) ? 0 : (score * 100 / total);

        // show simple completion message (no per-question feedback)
        JOptionPane.showMessageDialog(this, "Quiz Complete!\nYour Score: " + score + "/" + total + " (" + percent + "%)", "Result", JOptionPane.INFORMATION_MESSAGE);

        saveScore(score, total);
        persistQuestionVersions();

        // enable analytics/review/export/explanations buttons
        analyticsBtn.setEnabled(true);
        reviewBtn.setEnabled(true);
        exportBtn.setEnabled(true);
        explanationsBtn.setEnabled(true);
        exportExplanationsBtn.setEnabled(true);

        // show analytics and review automatically
        showAnalytics();
        openReviewWindow();

        // return to start screen
        cardLayout.show(mainPanel, "start");
    }

    // Save CSV scores (appends, creates header if needed)
    private void saveScore(int score, int total) {
        try {
            Path p = Paths.get(SCORES_FILE);
            boolean newFile = Files.notExists(p);
            String ts = ZonedDateTime.now().toString();
            try (BufferedWriter bw = Files.newBufferedWriter(p, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (newFile) bw.write("Roll,Name,Class,Subject,Score,Total,Timestamp\n");
                String line = String.join(",", escapeCsv(roll), escapeCsv(name), escapeCsv(cls), escapeCsv(subject),
                        String.valueOf(score), String.valueOf(total), escapeCsv(ts));
                bw.write(line + "\n");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to save score: " + e.getMessage(), "I/O Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    // ===== Score manager: full window with input boxes + table =====
    private void viewAndManageScores() {
        JFrame viewFrame = new JFrame("📋 View / Add / Delete Scores");
        viewFrame.setSize(900, 520);
        viewFrame.setLocationRelativeTo(this);
        viewFrame.setLayout(new BorderLayout(8,8));

        // Top: Input boxes to add a new score
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(new EmptyBorder(10,10,6,10));
        GridBagConstraints ic = new GridBagConstraints();
        ic.insets = new Insets(6,6,6,6);
        ic.fill = GridBagConstraints.HORIZONTAL;

        JTextField rollIn = new JTextField(10);
        JTextField nameIn = new JTextField(12);
        JTextField classIn = new JTextField(8);
        JTextField subjectIn = new JTextField(10);
        JTextField scoreIn = new JTextField(5);
        JTextField totalIn = new JTextField(5);

        ic.gridx = 0; ic.gridy = 0; inputPanel.add(new JLabel("Roll:"), ic);
        ic.gridx = 1; inputPanel.add(rollIn, ic);
        ic.gridx = 2; inputPanel.add(new JLabel("Name:"), ic);
        ic.gridx = 3; inputPanel.add(nameIn, ic);
        ic.gridx = 4; inputPanel.add(new JLabel("Class:"), ic);
        ic.gridx = 5; inputPanel.add(classIn, ic);

        ic.gridy = 1; ic.gridx = 0; inputPanel.add(new JLabel("Subject:"), ic);
        ic.gridx = 1; inputPanel.add(subjectIn, ic);
        ic.gridx = 2; inputPanel.add(new JLabel("Score:"), ic);
        ic.gridx = 3; inputPanel.add(scoreIn, ic);
        ic.gridx = 4; inputPanel.add(new JLabel("Total:"), ic);
        ic.gridx = 5; inputPanel.add(totalIn, ic);

        JButton addBtn = new JButton("➕ Add Score");
        addBtn.setPreferredSize(new Dimension(140, 30));
        ic.gridy = 2; ic.gridx = 0; ic.gridwidth = 6; inputPanel.add(addBtn, ic);

        viewFrame.add(inputPanel, BorderLayout.NORTH);

        // Center: Table showing scores
        String[] cols = new String[]{"Roll", "Name", "Class", "Subject", "Score", "Total", "Timestamp"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        viewFrame.add(scroll, BorderLayout.CENTER);

        // Bottom: actions
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        JButton deleteByRollBtn = new JButton("🗑 Delete by Roll No");
        JButton deleteAllBtn = new JButton("🧹 Delete All Scores");
        JButton closeBtn = new JButton("Close");
        bottom.add(deleteByRollBtn);
        bottom.add(deleteAllBtn);
        bottom.add(closeBtn);
        viewFrame.add(bottom, BorderLayout.SOUTH);

        // Load initial data
        List<String[]> rows = loadScoresCsvRows();
        for (String[] r : rows) model.addRow(r);

        // Add button action: validate, append to CSV, and refresh table
        addBtn.addActionListener(ev -> {
            String r = rollIn.getText().trim();
            String nm = nameIn.getText().trim();
            String cl = classIn.getText().trim();
            String subj = subjectIn.getText().trim();
            String sc = scoreIn.getText().trim();
            String tot = totalIn.getText().trim();
            if (r.isEmpty() || nm.isEmpty() || cl.isEmpty() || subj.isEmpty() || sc.isEmpty() || tot.isEmpty()) {
                JOptionPane.showMessageDialog(viewFrame, "Please fill all fields to add a score.", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                int scVal = Integer.parseInt(sc);
                int totVal = Integer.parseInt(tot);
                String ts = ZonedDateTime.now().toString();
                String line = String.join(",", escapeCsv(r), escapeCsv(nm), escapeCsv(cl), escapeCsv(subj), String.valueOf(scVal), String.valueOf(totVal), escapeCsv(ts));
                Path p = Paths.get(SCORES_FILE);
                boolean newFile = Files.notExists(p);
                try (BufferedWriter bw = Files.newBufferedWriter(p, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    if (newFile) bw.write("Roll,Name,Class,Subject,Score,Total,Timestamp\n");
                    bw.write(line + "\n");
                }
                model.addRow(new String[]{r, nm, cl, subj, String.valueOf(scVal), String.valueOf(totVal), ts});
                rollIn.setText(""); nameIn.setText(""); classIn.setText(""); subjectIn.setText(""); scoreIn.setText(""); totalIn.setText("");
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(viewFrame, "Score and Total must be integers.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            } catch (IOException io) {
                JOptionPane.showMessageDialog(viewFrame, "Unable to save score: " + io.getMessage(), "I/O Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Delete by roll
        deleteByRollBtn.addActionListener(ev -> {
            String rollToDelete = JOptionPane.showInputDialog(viewFrame, "Enter Roll No to delete:");
            if (rollToDelete != null && !rollToDelete.trim().isEmpty()) {
                deleteScoresByRoll(rollToDelete.trim());
                model.setRowCount(0);
                List<String[]> refreshed = loadScoresCsvRows();
                for (String[] r : refreshed) model.addRow(r);
            }
        });

        // Delete all
        deleteAllBtn.addActionListener(ev -> {
            int confirm = JOptionPane.showConfirmDialog(viewFrame, "Delete ALL scores? This cannot be undone.", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try { Files.deleteIfExists(Paths.get(SCORES_FILE)); } catch (IOException e) { JOptionPane.showMessageDialog(viewFrame, "Cannot delete scores file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
                model.setRowCount(0);
            }
        });

        closeBtn.addActionListener(ev -> viewFrame.dispose());

        viewFrame.setVisible(true);
    }

    // Load CSV rows as arrays of strings (skips empty lines)
    private List<String[]> loadScoresCsvRows() {
        List<String[]> rows = new ArrayList<>();
        Path p = Paths.get(SCORES_FILE);
        if (Files.notExists(p)) return rows;
        try {
            List<String> lines = Files.readAllLines(p);
            for (String ln : lines) {
                if (ln.trim().isEmpty()) continue;
                if (ln.toLowerCase().startsWith("roll,") && rows.isEmpty()) continue; // skip header
                List<String> tokens = new ArrayList<>();
                boolean inQuote = false;
                StringBuilder cur = new StringBuilder();
                for (int i = 0; i < ln.length(); i++) {
                    char ch = ln.charAt(i);
                    if (ch == '"' ) {
                        if (inQuote && i+1 < ln.length() && ln.charAt(i+1) == '"') { cur.append('"'); i++; continue; }
                        inQuote = !inQuote;
                    } else if (ch == ',' && !inQuote) {
                        tokens.add(cur.toString());
                        cur.setLength(0);
                    } else cur.append(ch);
                }
                tokens.add(cur.toString());
                while (tokens.size() < 7) tokens.add("");
                rows.add(tokens.subList(0,7).toArray(new String[0]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rows;
    }

    // Delete scores by roll
    private void deleteScoresByRoll(String rollNo) {
        Path path = Paths.get(SCORES_FILE);
        if (Files.notExists(path)) return;
        try {
            List<String> lines = Files.readAllLines(path);
            List<String> filtered = new ArrayList<>();
            for (String ln : lines) {
                if (ln.trim().isEmpty()) continue;
                if (ln.toLowerCase().startsWith("roll,")) { filtered.add(ln); continue; } // keep header
                String first = parseCsvFirstToken(ln);
                if (!first.equals(rollNo)) filtered.add(ln);
            }
            Files.write(path, filtered);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String parseCsvFirstToken(String line) {
        line = line.trim();
        if (line.startsWith("\"")) {
            int end = 1;
            while (end < line.length()) {
                if (line.charAt(end) == '"' && end+1 < line.length() && line.charAt(end+1) == '"') { end += 2; continue; }
                if (line.charAt(end) == '"') break;
                end++;
            }
            String token = line.substring(1, end).replace("\"\"", "\"");
            return token;
        } else {
            int comma = line.indexOf(',');
            if (comma == -1) return line;
            return line.substring(0, comma);
        }
    }

    // ===== Analytics visualization (small panel) =====
    private void showAnalytics() {
        JDialog dlg = new JDialog(this, "Performance Analytics", true);
        dlg.setSize(520, 340);
        dlg.setLocationRelativeTo(this);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(10,10,10,10));
        p.add(new JLabel("Performance Overview", SwingConstants.CENTER), BorderLayout.NORTH);

        // Chart area
        JPanel chart = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int w = getWidth(), h = getHeight();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // background
                g2.setColor(Color.WHITE);
                g2.fillRect(0,0,w,h);

                int barW = Math.max(60, w/6);
                int xOff = (w - 2*barW)/2;
                int base = h - 60;
                int max = Math.max(1, totalCorrectCount + totalIncorrectCount);
                int correctH = (int)Math.round(((double)totalCorrectCount / max) * (h - 140));
                int incorrectH = (int)Math.round(((double)totalIncorrectCount / max) * (h - 140));

                // correct bar
                g2.setColor(new Color(60,160,100));
                g2.fillRect(xOff, base - correctH, barW, correctH);
                g2.setColor(Color.BLACK);
                g2.drawString("Correct", xOff + 10, base + 20);
                g2.drawString(String.valueOf(totalCorrectCount), xOff + 10, base - correctH - 8);

                // incorrect bar
                g2.setColor(new Color(220,80,80));
                g2.fillRect(xOff + barW + 20, base - incorrectH, barW, incorrectH);
                g2.setColor(Color.BLACK);
                g2.drawString("Incorrect", xOff + barW + 30, base + 20);
                g2.drawString(String.valueOf(totalIncorrectCount), xOff + barW + 30, base - incorrectH - 8);

                g2.dispose();
            }
        };
        chart.setPreferredSize(new Dimension(480, 200));
        p.add(chart, BorderLayout.CENTER);

        dlg.add(p);
        dlg.setVisible(true);
    }

    // ===== Review Mode (table) =====
    private void openReviewWindow() {
        JFrame rev = new JFrame("🔁 Review: Answers & Explanations");
        rev.setSize(1000, 560);
        rev.setLocationRelativeTo(this);
        rev.setLayout(new BorderLayout(8,8));

        String[] cols = new String[]{"#", "Question (short)", "Your Answer", "Correct Answer", "Result", "Explanation", "Updated?"};
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(m);
        table.setRowHeight(36);
        JScrollPane sc = new JScrollPane(table);
        rev.add(sc, BorderLayout.CENTER);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            String shortQ = truncate(q.question, 80);
            String your = "";
            Integer sel = selectedDisplayedIndex[i];
            if (sel != null) {
                int[] map = displayedMappings.get(i);
                if (map != null && sel >= 0 && sel < map.length) your = q.options[map[sel]];
            }
            String corr = q.options[q.correctIndex];
            boolean correct = false;
            if (sel != null && displayedMappings.get(i) != null) {
                correct = displayedMappings.get(i)[sel] == q.correctIndex;
            }
            String result = correct ? "Correct" : "Incorrect";
            String expl = q.explanation == null ? "" : q.explanation;
            // version check
            String id = qId(q);
            String currentHash = computeQuestionHash(q);
            boolean updated = false;
            String persistedHash = loadedQuestionHashes.get(id);
            if (persistedHash != null && !persistedHash.equals(currentHash)) updated = true;
            m.addRow(new String[]{String.valueOf(i+1), shortQ, your, corr, result, expl, updated ? "Yes" : "No"});
        }

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        JButton exportBtn = new JButton("📄 Export Review / Save as PDF");
        exportBtn.addActionListener(e -> exportReviewAsPDF());
        JButton close = new JButton("Close");
        close.addActionListener(e -> rev.dispose());
        bottom.add(exportBtn);
        bottom.add(close);
        rev.add(bottom, BorderLayout.SOUTH);

        rev.setVisible(true);
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        if (s.length() <= len) return s;
        return s.substring(0, len-3) + "...";
    }

    // ===== Question hashing & versioning persistence =====
    private String qId(Question q) {
        return Integer.toString(Objects.hash(q.subject, q.question)); // deterministic-ish id
    }

    private String computeQuestionHash(Question q) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String concat = q.subject + "||" + q.question + "||" + String.join("||", q.options) + "||" + q.explanation;
            byte[] b = md.digest(concat.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte by : b) sb.append(String.format("%02x", by));
            return sb.toString();
        } catch (Exception e) { return Integer.toString(Objects.hash(q.subject, q.question, Arrays.toString(q.options))); }
    }

    private void loadPersistedQuestionVersions() {
        loadedQuestionHashes.clear();
        Path p = Paths.get(VERSIONS_FILE);
        if (Files.notExists(p)) return;
        try {
            List<String> lines = Files.readAllLines(p);
            for (String ln : lines) {
                if (ln.trim().isEmpty()) continue;
                int idx = ln.indexOf(',');
                if (idx > 0) {
                    String id = ln.substring(0, idx);
                    String hash = ln.substring(idx+1);
                    loadedQuestionHashes.put(id, hash);
                }
            }
        } catch (IOException ignored) {}
    }

    private void persistQuestionVersions() {
        // merge loaded + current session's questionVersions
        try {
            Map<String,String> merged = new HashMap<>(loadedQuestionHashes);
            merged.putAll(questionVersions);
            Path p = Paths.get(VERSIONS_FILE);
            try (BufferedWriter bw = Files.newBufferedWriter(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Map.Entry<String,String> e : merged.entrySet()) {
                    bw.write(e.getKey() + "," + e.getValue() + "\n");
                }
            }
            // also update runtime loaded map
            loadedQuestionHashes.putAll(questionVersions);
        } catch (IOException ignored) {}
    }

    // ===== Export / Print review =====
    private void exportReviewAsPDF() {
        if (questions == null || questions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No review available to export.", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // Ask file to save
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Review as PDF (attempt)");
        fc.setSelectedFile(new File(System.getProperty("user.home"), "skillnest_review.pdf"));
        int res = fc.showSaveDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File target = fc.getSelectedFile();
        if (!target.getName().toLowerCase().endsWith(".pdf")) {
            target = new File(target.getParentFile(), target.getName() + ".pdf");
        }

        // Try to find StreamPrintServiceFactory supporting PDF
        boolean pdfSaved = false;
        try {
            DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
            StreamPrintServiceFactory[] factories = StreamPrintServiceFactory.lookupStreamPrintServiceFactories(flavor, "application/pdf");
            if (factories != null && factories.length > 0) {
                try (FileOutputStream fos = new FileOutputStream(target)) {
                    StreamPrintService sps = factories[0].getPrintService(fos);
                    PrinterJob pj = PrinterJob.getPrinterJob();
                    pj.setPrintable(makePrintableForReview());
                    pj.setPrintService(sps);
                    pj.print();
                    pdfSaved = true;
                    JOptionPane.showMessageDialog(this, "Exported review to PDF:\n" + target.getAbsolutePath(), "Export", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                System.err.println("No StreamPrintServiceFactory available for application/pdf on this JVM.");
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.err.println("Direct PDF export attempt failed: " + ex.getMessage());
        }

        // Always save PNG fallback (guaranteed)
        try {
            BufferedImage img = renderReviewToImage();
            File pngFile = new File(target.getParentFile(), target.getName().replaceAll("\\.pdf$", "") + ".png");
            ImageIO.write(img, "png", pngFile);
            JOptionPane.showMessageDialog(this,
                    (pdfSaved ? "Also saved PNG fallback at:\n" : "Saved PNG fallback at:\n")
                            + pngFile.getAbsolutePath(),
                    "Saved PNG", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            JOptionPane.showMessageDialog(this, "Unable to save PNG fallback: " + ioe.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }

        // If PDF not saved, offer the system Print dialog (user can choose "Save as PDF")
        if (!pdfSaved) {
            int opt = JOptionPane.showConfirmDialog(this,
                    "Direct PDF export is not available on this JVM.\nWould you like to open the system Print dialog so you can choose 'Save as PDF' (if supported)?",
                    "Print to PDF?", JOptionPane.YES_NO_OPTION);
            if (opt == JOptionPane.YES_OPTION) {
                PrinterJob pj = PrinterJob.getPrinterJob();
                pj.setPrintable(makePrintableForReview());
                if (pj.printDialog()) {
                    try { pj.print(); }
                    catch (PrinterException pe) {
                        pe.printStackTrace();
                        JOptionPane.showMessageDialog(this, "Printing failed: " + pe.getMessage(), "Print Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }

    private Printable makePrintableForReview() {
        return new Printable() {
            public int print(Graphics g, PageFormat pf, int pageIndex) throws PrinterException {
                int perPage = 6;
                int totalPages = (int)Math.ceil((double)questions.size() / perPage);
                if (pageIndex >= totalPages) return NO_SUCH_PAGE;
                Graphics2D g2 = (Graphics2D) g;
                g2.translate(pf.getImageableX(), pf.getImageableY());
                int w = (int) pf.getImageableWidth();
                int y = 0;
                g2.setFont(new Font("Serif", Font.BOLD, 14));
                g2.drawString("SkillNest — Review Report", 0, y += 18);
                g2.setFont(new Font("Serif", Font.PLAIN, 11));
                g2.drawString("Student: " + name + "   Roll: " + roll + "   Class: " + cls + "   Subject: " + subject, 0, y += 18);
                g2.drawString("Page " + (pageIndex+1) + " of " + totalPages, 0, y += 18);
                y += 8;
                int start = pageIndex * perPage;
                int end = Math.min(questions.size(), start + perPage);
                for (int i = start; i < end; i++) {
                    Question q = questions.get(i);
                    String qn = (i+1) + ". " + q.question;
                    y = drawStringWrapped(g2, qn, 0, y, w, 12);
                    Integer sel = selectedDisplayedIndex[i];
                    String your = "";
                    if (sel != null && displayedMappings.get(i) != null) {
                        int orig = displayedMappings.get(i)[sel];
                        your = q.options[orig];
                    } else your = "<no answer>";
                    String corr = q.options[q.correctIndex];
                    g2.drawString("Your: " + your, 10, y += 14);
                    g2.drawString("Correct: " + corr, 10, y += 14);
                    if (q.explanation != null && !q.explanation.trim().isEmpty()) {
                        y = drawStringWrapped(g2, "Explanation: " + q.explanation, 10, y + 2, w-20, 12);
                    } else y += 8;
                    boolean updated = false;
                    String id = qId(q);
                    String curHash = computeQuestionHash(q);
                    String prevHash = loadedQuestionHashes.get(id);
                    if (prevHash != null && !prevHash.equals(curHash)) updated = true;
                    if (updated) g2.drawString("Note: Question updated since last session.", 10, y += 14);
                    y += 10;
                    if (y > pf.getImageableHeight() - 60) break;
                }
                return PAGE_EXISTS;
            }
        };
    }

    private int drawStringWrapped(Graphics2D g2, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            int tw = fm.stringWidth(test);
            if (tw > maxWidth && line.length() > 0) {
                g2.drawString(line.toString(), x, y += lineHeight);
                line = new StringBuilder(w);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(w);
            }
        }
        if (line.length() > 0) g2.drawString(line.toString(), x, y += lineHeight);
        return y;
    }

    // helper used during renderReviewToImage - similar to drawStringWrapped but returns Y
    private int drawStringWrappedImage(Graphics2D g2, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            int tw = fm.stringWidth(test);
            if (tw > maxWidth && line.length() > 0) {
                g2.drawString(line.toString(), x, y += lineHeight);
                line = new StringBuilder(w);
            } else {
                if (line.length() > 0) line.append(" ");
                line.append(w);
            }
        }
        if (line.length() > 0) g2.drawString(line.toString(), x, y += lineHeight);
        return y;
    }

    private BufferedImage renderReviewToImage() {
        int width = 1000;
        int y = 20;
        BufferedImage tmp = new BufferedImage(width, 2000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = tmp.createGraphics();
        g2.setColor(Color.WHITE); g2.fillRect(0,0,tmp.getWidth(), tmp.getHeight());
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Serif", Font.BOLD, 16));
        g2.drawString("SkillNest — Review Report", 10, y); y += 24;
        g2.setFont(new Font("Serif", Font.PLAIN, 12));
        g2.drawString("Student: " + name + "   Roll: " + roll + "   Class: " + cls + "   Subject: " + subject, 10, y); y += 20;
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            g2.setFont(new Font("Serif", Font.PLAIN, 12));
            y = drawStringWrappedImage(g2, (i+1) + ". " + q.question, 10, y, width-40, 16);
            Integer sel = selectedDisplayedIndex[i];
            String your = "";
            if (sel != null && displayedMappings.get(i) != null) {
                int orig = displayedMappings.get(i)[sel];
                your = q.options[orig];
            } else your = "<no answer>";
            g2.drawString("Your: " + your, 18, y += 16);
            g2.drawString("Correct: " + q.options[q.correctIndex], 18, y += 16);
            if (q.explanation != null && !q.explanation.trim().isEmpty()) {
                y = drawStringWrappedImage(g2, "Explanation: " + q.explanation, 18, y + 8, width-40, 14);
            } else y += 8;
            boolean updated = false;
            String id = qId(q);
            String curHash = computeQuestionHash(q);
            String prevHash = loadedQuestionHashes.get(id);
            if (prevHash != null && !prevHash.equals(curHash)) updated = true;
            if (updated) { g2.drawString("Note: Question updated since last session.", 18, y += 16); }
            y += 18;
            if (y > tmp.getHeight() - 200) {
                BufferedImage bigger = new BufferedImage(width, tmp.getHeight() + 2000, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g3 = bigger.createGraphics();
                g3.drawImage(tmp, 0, 0, null);
                g3.setColor(Color.WHITE);
                g3.fillRect(0, tmp.getHeight(), bigger.getWidth(), bigger.getHeight() - tmp.getHeight());
                g2.dispose();
                tmp = bigger;
                g2 = tmp.createGraphics();
            }
        }
        g2.dispose();
        BufferedImage trimmed = tmp.getSubimage(0,0, tmp.getWidth(), Math.min(tmp.getHeight(), Math.max(300, y+40)));
        return trimmed;
    }

    // ===== New: Show all brief explanations in one dialog =====
    private void showAllExplanations() {
        if (questions == null || questions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No questions loaded.", "Explanations", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            String expl = q.explanation == null ? "" : q.explanation.trim();
            if (expl.isEmpty()) expl = "(no explanation provided)";
            sb.append(i+1).append(". ").append(truncate(q.question, 80)).append("\n   -> ").append(truncate(expl, 200)).append("\n\n");
        }
        JTextArea area = new JTextArea(sb.toString());
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane sp = new JScrollPane(area);
        sp.setPreferredSize(new Dimension(700, 420));
        JOptionPane.showMessageDialog(this, sp, "All Explanations (Brief)", JOptionPane.INFORMATION_MESSAGE);
    }

    // ===== Dependency-free paginated PDF exporter for explanations =====
    // Public method wired to Export Explanations button
    private void exportExplanationsAsPDF() {
        if (questions == null || questions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No questions loaded.", "Export Explanations", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Build lines (wrapped roughly by char count)
        List<String> rawParagraphs = new ArrayList<>();
        rawParagraphs.add("SkillNest — Explanations");
        rawParagraphs.add(String.format("Student: %s   Roll: %s   Class: %s   Subject: %s",
                name == null ? "" : name,
                roll == null ? "" : roll,
                cls == null ? "" : cls,
                subject == null ? "" : subject));
        rawParagraphs.add(""); // blank line
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            String expl = (q.explanation == null || q.explanation.trim().isEmpty()) ? "(no explanation provided)" : q.explanation.trim();
            rawParagraphs.add((i+1) + ". " + q.question);
            rawParagraphs.add("Explanation: " + expl);
            rawParagraphs.add(""); // spacer
        }

        // Wrap each paragraph to ~95 chars per line (tweakable)
        List<String> wrappedLines = new ArrayList<>();
        int maxChars = 95;
        for (String para : rawParagraphs) {
            if (para == null || para.isEmpty()) { wrappedLines.add(""); continue; }
            int idx = 0;
            while (idx < para.length()) {
                int end = Math.min(para.length(), idx + maxChars);
                if (end < para.length()) {
                    int lastSpace = para.lastIndexOf(' ', end);
                    if (lastSpace > idx) end = lastSpace;
                }
                wrappedLines.add(para.substring(idx, end));
                idx = end;
                while (idx < para.length() && para.charAt(idx) == ' ') idx++;
            }
        }

        // Ask where to save
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Explanations as PDF");
        fc.setSelectedFile(new File(System.getProperty("user.home"), "skillnest_explanations.pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".pdf")) out = new File(out.getParentFile(), out.getName() + ".pdf");

        // lines per page (approx). Tweak to change font/spacing.
        int linesPerPage = 60;
        try {
            writePagedSimplePdf(out, wrappedLines, linesPerPage);
            JOptionPane.showMessageDialog(this, "Explanations exported to:\n" + out.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to write PDF: " + ioe.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Minimal paginated PDF writer (no external libs).
     * - wrappedLines: already-wrapped lines of plain text (no parentheses escaping needed).
     * - linesPerPage: number of text lines per PDF page (approx).
     *
     * Limitations: Uses Type1 Helvetica, basic text placement; designed for plain text exports.
     */
    private void writePagedSimplePdf(File file, List<String> wrappedLines, int linesPerPage) throws IOException {
        // (Implementation identical to earlier single-file version)
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < wrappedLines.size(); i += linesPerPage) {
            int end = Math.min(wrappedLines.size(), i + linesPerPage);
            pages.add(new ArrayList<>(wrappedLines.subList(i, end)));
        }
        if (pages.isEmpty()) pages.add(Collections.singletonList(""));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        RunnableWriter w = new RunnableWriter(baos);

        // PDF header
        w.write("%PDF-1.4\n");
        w.write("%\u00E2\u00E3\u00CF\u00D3\n"); // binary comment

        List<Integer> xrefOffsets = new ArrayList<>();
        xrefOffsets.add(0);

        int pageCount = pages.size();
        int firstPageObjNum = 3;
        int fontObjNum = firstPageObjNum + pageCount * 2;

        // Object 1: Catalog
        xrefOffsets.add(baos.size());
        w.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        // Object 2: Pages
        xrefOffsets.add(baos.size());
        StringBuilder kidsSb = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            int pageObjNumber = firstPageObjNum + (i * 2);
            kidsSb.append(pageObjNumber).append(" 0 R ");
        }
        w.write("2 0 obj\n<< /Type /Pages /Kids [ " + kidsSb.toString().trim() + " ] /Count " + pageCount + " >>\nendobj\n");

        // Font obj
        xrefOffsets.add(baos.size());
        w.write(fontObjNum + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");

        List<byte[]> contentBytesList = new ArrayList<>();
        for (int p = 0; p < pageCount; p++) {
            List<String> lines = pages.get(p);
            StringBuilder content = new StringBuilder();
            content.append("BT\n");
            content.append("/F1 10 Tf\n");
            content.append("50 800 Td\n");
            for (int li = 0; li < lines.size(); li++) {
                String ln = escapePdfText(lines.get(li));
                content.append("(").append(ln).append(") Tj\n");
                if (li < lines.size() - 1) content.append("0 -12 Td\n");
            }
            content.append("ET\n");
            byte[] cb = content.toString().getBytes(StandardCharsets.UTF_8);
            contentBytesList.add(cb);
        }

        for (int p = 0; p < pageCount; p++) {
            int pageObjNum = firstPageObjNum + (p * 2);
            int contentObjNum = pageObjNum + 1;

            xrefOffsets.add(baos.size());
            String pageObj = pageObjNum + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 " + fontObjNum + " 0 R >> >> /Contents " + contentObjNum + " 0 R >>\nendobj\n";
            w.write(pageObj);

            xrefOffsets.add(baos.size());
            byte[] cb = contentBytesList.get(p);
            w.write(contentObjNum + " 0 obj\n<< /Length " + cb.length + " >>\nstream\n");
            baos.write(cb);
            w.write("\nendstream\nendobj\n");
        }

        int xrefStart = baos.size();
        int totalObjects = fontObjNum;

        Map<Integer, Integer> offsetsMap = new TreeMap<>();
        int idx = 1;
        offsetsMap.put(1, xrefOffsets.get(idx++));
        offsetsMap.put(2, xrefOffsets.get(idx++));
        offsetsMap.put(fontObjNum, xrefOffsets.get(idx++));
        for (int p = 0; p < pageCount; p++) {
            int pageObjNum = firstPageObjNum + (p * 2);
            int contentObjNum = pageObjNum + 1;
            offsetsMap.put(pageObjNum, xrefOffsets.get(idx++));
            offsetsMap.put(contentObjNum, xrefOffsets.get(idx++));
        }

        w.write("xref\n0 " + (totalObjects + 1) + "\n");
        w.write(String.format("%010d %05d f \n", 0, 65535));
        for (int objNum = 1; objNum <= totalObjects; objNum++) {
            Integer off = offsetsMap.get(objNum);
            if (off == null) off = 0;
            w.write(String.format("%010d %05d n \n", off, 0));
        }

        w.write("trailer\n<< /Size " + (totalObjects + 1) + " /Root 1 0 R >>\n");
        w.write("startxref\n" + xrefStart + "\n%%EOF\n");

        try (FileOutputStream fos = new FileOutputStream(file)) {
            baos.writeTo(fos);
        }
    }

    // Escape parentheses and backslashes for PDF text literal.
    private String escapePdfText(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    // reuse the small helper writer
    private static class RunnableWriter {
        private final OutputStream out;
        RunnableWriter(OutputStream out) { this.out = out; }
        void write(String s) throws IOException { out.write(s.getBytes(StandardCharsets.UTF_8)); }
    }

    // Main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SkillNestStudentQuiz s = new SkillNestStudentQuiz();
            s.setVisible(true);
        });
    }

    // Helper UI classes
    static class RoundedBorder extends LineBorder {
        private int radius;
        RoundedBorder(int radius, Color color) { super(color, 1, true); this.radius = radius; }
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, width-1, height-1, radius, radius);
            g2.dispose();
        }
    }
    static class DropShadowBorder extends EmptyBorder {
        DropShadowBorder() { super(8, 8, 8, 8); }
    }
}
