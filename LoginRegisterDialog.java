// LoginRegisterDialog.java
// Modal login / register dialog with friendly Enter-key navigation and automatic switch-to-login after register.

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

class LoginRegisterDialog {
    private final JDialog dlg;
    private final UserStore store = new UserStore();
    private String loggedInUser = null;

    // UI components we need to access between tabs
    private JTabbedPane tabs;
    private JButton loginBtn;
    private JButton registerBtn;

    // login fields
    private JTextField loginUserField;
    private JPasswordField loginPassField;

    // register fields
    private JTextField regUserField;
    private JPasswordField regPassField;
    private JPasswordField regPass2Field;

    LoginRegisterDialog(Window owner) {
        dlg = new JDialog(owner, "Login / Register", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setSize(460, 320);
        dlg.setLocationRelativeTo(owner);

        tabs = new JTabbedPane();
        tabs.addTab("Login", buildLoginPanel());
        tabs.addTab("Register", buildRegisterPanel());

        // When switching tabs, update default button so Enter key triggers the visible tab's main action
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 0) {
                // login tab
                dlg.getRootPane().setDefaultButton(loginBtn);
                SwingUtilities.invokeLater(() -> {
                    if (loginUserField.getText().trim().isEmpty()) loginUserField.requestFocusInWindow();
                    else loginPassField.requestFocusInWindow();
                });
            } else {
                dlg.getRootPane().setDefaultButton(registerBtn);
                SwingUtilities.invokeLater(() -> {
                    if (regUserField.getText().trim().isEmpty()) regUserField.requestFocusInWindow();
                    else if (String.valueOf(regPassField.getPassword()).isEmpty()) regPassField.requestFocusInWindow();
                    else regPass2Field.requestFocusInWindow();
                });
            }
        });

        dlg.getContentPane().add(tabs, BorderLayout.CENTER);

        // default to Login tab
        tabs.setSelectedIndex(0);
        dlg.getRootPane().setDefaultButton(loginBtn);
    }

    private JPanel buildLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8,8,8,8);
        c.fill = GridBagConstraints.HORIZONTAL;

        loginUserField = new JTextField(20);
        loginPassField = new JPasswordField(20);

        c.gridx=0; c.gridy=0; p.add(new JLabel("Username:"), c);
        c.gridx=1; p.add(loginUserField, c);
        c.gridx=0; c.gridy=1; p.add(new JLabel("Password:"), c);
        c.gridx=1; p.add(loginPassField, c);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loginBtn = new JButton("Login");
        JButton cancel = new JButton("Cancel");
        btnRow.add(loginBtn); btnRow.add(cancel);

        c.gridx=0; c.gridy=2; c.gridwidth=2; p.add(btnRow, c);

        // Enter behaviour:
        // username Enter -> focus password
        loginUserField.addActionListener(e -> loginPassField.requestFocusInWindow());
        // password Enter -> trigger login button
        loginPassField.addActionListener(e -> loginBtn.doClick());

        loginBtn.addActionListener(e -> {
            String u = loginUserField.getText().trim();
            String pw = new String(loginPassField.getPassword());
            if (u.isEmpty() || pw.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Please enter username and password.", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                if (store.authenticateUser(u, pw)) {
                    loggedInUser = u;
                    dlg.dispose();
                } else {
                    JOptionPane.showMessageDialog(dlg, "Invalid username or password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
                    loginPassField.setText("");
                    loginPassField.requestFocusInWindow();
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dlg, "Unable to access user store: " + ex.getMessage(), "I/O Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancel.addActionListener(e -> dlg.dispose());

        return p;
    }

    private JPanel buildRegisterPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8,8,8,8);
        c.fill = GridBagConstraints.HORIZONTAL;

        regUserField = new JTextField(20);
        regPassField = new JPasswordField(20);
        regPass2Field = new JPasswordField(20);

        c.gridx=0; c.gridy=0; p.add(new JLabel("Choose Username:"), c);
        c.gridx=1; p.add(regUserField, c);
        c.gridx=0; c.gridy=1; p.add(new JLabel("Password:"), c);
        c.gridx=1; p.add(regPassField, c);
        c.gridx=0; c.gridy=2; p.add(new JLabel("Confirm Password:"), c);
        c.gridx=1; p.add(regPass2Field, c);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        registerBtn = new JButton("Register");
        JButton cancel = new JButton("Cancel");
        btnRow.add(registerBtn); btnRow.add(cancel);

        c.gridx=0; c.gridy=3; c.gridwidth=2; p.add(btnRow, c);

        // Enter behaviour:
        // reg username Enter -> password
        regUserField.addActionListener(e -> regPassField.requestFocusInWindow());
        // reg password Enter -> confirm password
        regPassField.addActionListener(e -> regPass2Field.requestFocusInWindow());
        // reg confirm Enter -> trigger register action
        regPass2Field.addActionListener(e -> registerBtn.doClick());

        registerBtn.addActionListener(e -> {
            String u = regUserField.getText().trim();
            String pw = new String(regPassField.getPassword());
            String pw2 = new String(regPass2Field.getPassword());
            if (u.isEmpty() || pw.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Please fill username and password.", "Input Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!pw.equals(pw2)) {
                JOptionPane.showMessageDialog(dlg, "Passwords do not match.", "Input Error", JOptionPane.WARNING_MESSAGE);
                regPassField.setText("");
                regPass2Field.setText("");
                regPassField.requestFocusInWindow();
                return;
            }
            try {
                boolean ok = store.registerUser(u, pw);
                if (ok) {
                    JOptionPane.showMessageDialog(dlg, "Registration successful. Switching to login...", "Registered", JOptionPane.INFORMATION_MESSAGE);
                    // switch to login tab and pre-fill username, clear password, and focus password
                    SwingUtilities.invokeLater(() -> {
                        loginUserField.setText(u);
                        loginPassField.setText("");
                        tabs.setSelectedIndex(0); // switch to Login tab
                        dlg.getRootPane().setDefaultButton(loginBtn);
                        loginPassField.requestFocusInWindow();
                    });
                } else {
                    JOptionPane.showMessageDialog(dlg, "Username already exists. Choose another.", "Exists", JOptionPane.WARNING_MESSAGE);
                    regUserField.requestFocusInWindow();
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dlg, "Unable to update user store: " + ex.getMessage(), "I/O Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancel.addActionListener(e -> dlg.dispose());
        return p;
    }

    /**
     * Shows the modal dialog; returns the logged-in username or null if cancelled.
     */
    String showAndReturnUser() {
        // ensure focus and default button are correct when shown
        SwingUtilities.invokeLater(() -> {
            tabs.setSelectedIndex(0);
            loginUserField.requestFocusInWindow();
            dlg.getRootPane().setDefaultButton(loginBtn);
        });
        dlg.setVisible(true);
        return loggedInUser;
    }
}
