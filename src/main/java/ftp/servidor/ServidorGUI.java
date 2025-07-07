package ftp.servidor;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServidorGUI extends JFrame {
    private final JTextPane logPane;
    private final JTextField portaField;
    private final JButton toggleButton;

    private Servidor servidor;
    private int portaAtual = 12381;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss");

    // Define as cores para os logs
    private final Color COR_ERRO = new Color(210, 4, 45);
    private final Color COR_AVISO = new Color(255, 127, 0);
    private final Color COR_SUCESSO = new Color(0, 128, 0);
    private final Color COR_INFO = new Color(0, 100, 200);
    private final Color COR_PADRAO = Color.BLACK; // Preto
    private final Color COR_TIMESTAMP = Color.GRAY; // Cinza para o horário

    public ServidorGUI() {
        setTitle("Painel de Controle do Servidor FTP");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.setBorder(new TitledBorder("Controles"));

        controlPanel.add(new JLabel("Porta:"));
        portaField = new JTextField(String.valueOf(portaAtual), 6);
        controlPanel.add(portaField);

        toggleButton = new JButton("Iniciar Servidor");
        toggleButton.addActionListener(e -> toggleServidor());
        controlPanel.add(toggleButton);

        add(controlPanel, BorderLayout.NORTH);

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(new Font("Arial", Font.BOLD, 12));
        SimpleAttributeSet paragraphStyle = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(paragraphStyle, 0.5f);
        StyleConstants.setBold(paragraphStyle, true);
        logPane.setParagraphAttributes(paragraphStyle, true);

        JScrollPane scrollPane = new JScrollPane(logPane);
        scrollPane.setBorder(new TitledBorder("Log de Atividade"));
        add(scrollPane, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                pararServidorComConfirmacao();
            }
        });

        setSize(800, 600);
        setLocationRelativeTo(null); // Centralizar na tela
    }

    private void toggleServidor() {
        if (servidor != null && servidor.isRunning()) {
            pararServidor();
        } else {
            iniciarServidor();
        }
    }

    private void iniciarServidor() {
        try {
            int novaPorta = Integer.parseInt(portaField.getText().trim());
            if (novaPorta < 1 || novaPorta > 65535) {
                JOptionPane.showMessageDialog(this, "Porta inválida. Use um valor entre 1 e 65535.", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            portaAtual = novaPorta;

            servidor = new Servidor(portaAtual, this::adicionarLog);
            new Thread(servidor::start).start();

            toggleButton.setText("Parar Servidor");
            toggleButton.setForeground(Color.WHITE);
            toggleButton.setForeground(COR_ERRO);
            portaField.setEnabled(false);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "O valor da porta deve ser um número.", "Erro de Formato", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pararServidor() {
        if (servidor != null) {
            servidor.stop();
            toggleButton.setText("Iniciar Servidor");
            toggleButton.setForeground(COR_PADRAO);
            toggleButton.setBackground(null);
            portaField.setEnabled(true);
        }
    }

    private void pararServidorComConfirmacao() {
        int resposta = JOptionPane.showConfirmDialog(
                this,
                "Deseja realmente parar o servidor e fechar a aplicação?",
                "Confirmar Saída",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (resposta == JOptionPane.YES_OPTION) {
            pararServidor();
            dispose();
            System.exit(0);
        }
    }

    private void appendColorido(String texto, Color cor) {
        StyleContext sc = StyleContext.getDefaultStyleContext();
        AttributeSet aset = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, cor);
        aset = sc.addAttribute(aset, StyleConstants.FontFamily, "Arial");
        aset = sc.addAttribute(aset, StyleConstants.FontSize, 12);

        StyledDocument doc = logPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), texto, aset);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    public void adicionarLog(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = String.format("[%s] ", LocalDateTime.now().format(formatter));
            appendColorido(timestamp, COR_TIMESTAMP); // Adiciona o timestamp em cinza

            String lowerCaseMsg = mensagem.toLowerCase();
            Color cor;

            if (lowerCaseMsg.contains("erro")) {
                cor = COR_ERRO;
            } else if (lowerCaseMsg.contains("aviso")) {
                cor = COR_AVISO;
            } else if (lowerCaseMsg.contains("sucesso") || lowerCaseMsg.contains("concluído")) {
                cor = COR_SUCESSO;
            } else if (lowerCaseMsg.contains("conexão") || lowerCaseMsg.contains("iniciado") || lowerCaseMsg.contains("aceita")) {
                cor = COR_INFO;
            } else {
                cor = COR_PADRAO;
            }

            appendColorido(mensagem + "\n", cor); // Adiciona a mensagem com a cor escolhida

            logPane.setCaretPosition(logPane.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Erro ao configurar a aparência: " + e.getMessage());
        }
        SwingUtilities.invokeLater(() -> new ServidorGUI().setVisible(true));
    }
}