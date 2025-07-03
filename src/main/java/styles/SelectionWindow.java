package styles;

import ftp.cliente.Cliente;
import ftp.servidor.ServidorGUI;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class SelectionWindow extends JFrame {
    public SelectionWindow () {
        super("Aplicação FTP - Modo de execução");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 200);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel painelPrincipal = new JPanel(new BorderLayout(15, 15));
        painelPrincipal.setBackground(Color.WHITE);
        painelPrincipal.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titulo = new JLabel("Modo de execução", SwingConstants.CENTER);
        titulo.setFont(new Font("Arial", Font.BOLD, 16));
        titulo.setForeground(new Color(45, 52, 54));
        JLabel subtitulo = new JLabel("Selecione como deseja iniciar a aplicação", SwingConstants.CENTER);
        subtitulo.setFont(new Font("Arial", Font.PLAIN, 12));
        subtitulo.setForeground(Color.GRAY);
        JPanel painelTexto = new JPanel(new BorderLayout());
        painelTexto.setBackground(Color.WHITE);
        painelTexto.add(titulo, BorderLayout.NORTH);
        painelTexto.add(subtitulo, BorderLayout.CENTER);
        painelPrincipal.add(painelTexto, BorderLayout.NORTH);

        JPanel painelBotoes = new JPanel(new GridLayout(1, 2, 15, 0));
        painelBotoes.setBackground(Color.WHITE);

        JButton btnServidor = createStyledButton("Servidor");
        JButton btnCliente = createStyledButton("Cliente");

        painelBotoes.add(btnServidor);
        painelBotoes.add(btnCliente);
        painelPrincipal.add(painelBotoes, BorderLayout.CENTER);

        btnServidor.addActionListener(e -> {
            ServidorGUI.main(null);
            dispose();
        });
        btnCliente.addActionListener(e -> {
            Cliente.main(null);
            dispose();
        });

        setContentPane(painelPrincipal);
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(245, 245, 245));
        button.setForeground(new Color(60, 60, 60));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        button.setFocusPainted(false);

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(225, 236, 255));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(0, 120, 215), 1),
                        BorderFactory.createEmptyBorder(5, 10, 5, 10)
                ));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(245, 245, 245));
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                        BorderFactory.createEmptyBorder(5, 10, 5, 10)
                ));
            }
        });
        return button;
    }
}