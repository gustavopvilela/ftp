package ftp;

import styles.SelectionWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.out.println("Erro: " + e.getMessage());
            }
            SelectionWindow janela = new SelectionWindow();
            janela.setVisible(true);
        });
    }
}