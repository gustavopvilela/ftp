package ftp;

import utils.FolderIdUtil;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Cliente extends JFrame {
    private static final String HOST = "localhost";
    private static final int PORTA = 12381;

    /* Componentes da interface */
    private JLabel statusLabel;
    private JButton uploadButton;
    private JList<String> pastasServidorList;
    private DefaultListModel<String> listModel;
    private JButton downloadButton;
    private JTextArea logArea;

    private JTextField hostField;
    private JTextField portaField;
    private JButton conectarButton;
    private JLabel conexaoStatusLabel;

    /* Estado da aplicação */
    private File pastaSelecionada;
    private String pastaSelecionadaId;
    private String hostAtual;
    private int portaAtual;
    private boolean conectado = false;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss:SSS");

    public Cliente() {
        hostAtual = HOST;
        portaAtual = PORTA;
        inicializarGUI();
    }

    public void inicializarGUI() {
        setTitle("Cliente FTP");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel conexaoPanel = createConexaoPanel();
        JPanel uploadPanel = createUploadPanel();
        JPanel servidorPanel = createServidorPanel();
        JPanel logPanel = createLogPanel();
        JPanel statusPanel = createStatusPanel();

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.add(conexaoPanel);
        topPanel.add(uploadPanel);

        add(topPanel, BorderLayout.NORTH);
        add(servidorPanel, BorderLayout.CENTER);
        add(logPanel, BorderLayout.EAST);
        add(statusPanel, BorderLayout.SOUTH);

        setSize(1000, 700);
        setLocationRelativeTo(null);

        atualizarEstadoConexao();
    }

    private JPanel createConexaoPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Configuração do servidor"));

        panel.add(new JLabel("Servidor:"));
        hostField = new JTextField(hostAtual, 15);
        hostField.setToolTipText("Digite o IP do servidor FTP");
        panel.add(hostField);

        panel.add(new JLabel("Porta:"));
        portaField = new JTextField(String.valueOf(portaAtual), 6);
        portaField.setToolTipText("Digite a porta do servidor FTP");
        panel.add(portaField);

        conectarButton = new JButton("Testar conexão");
        conectarButton.addActionListener(this::testarConexao);
        panel.add(conectarButton);

        conexaoStatusLabel = new JLabel("Não conectado");
        conexaoStatusLabel.setForeground(Color.RED);
        panel.add(conexaoStatusLabel);

        return panel;
    }

    private void testarConexao(ActionEvent e) {
        String novoHost = hostField.getText().trim();
        String portaTexto = portaField.getText().trim();

        if (novoHost.isEmpty()) {
            mostrarErro("Por favor, digite o endereço do servidor");
            return;
        }

        int novaPorta;
        try {
            novaPorta = Integer.parseInt(portaTexto);
            if (novaPorta < 1 || novaPorta > 65535) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            mostrarErro("Porta inválida. Digite um número entre 1 e 65535");
            return;
        }

        conectarButton.setEnabled(false);
        conexaoStatusLabel.setText("Testando...");
        conexaoStatusLabel.setForeground(Color.ORANGE);

        new Thread(() -> {
            try (Socket socket = new Socket(novoHost, novaPorta)) {
                socket.setSoTimeout(5000); // 5 segundos de timeout
                BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String resposta = entrada.readLine();
                if (resposta != null && resposta.startsWith("220")) {
                    SwingUtilities.invokeLater(() -> {
                        hostAtual = novoHost;
                        portaAtual = novaPorta;
                        conectado = true;
                        conexaoStatusLabel.setText("Conectado (" + hostAtual + ":" + portaAtual + ")");
                        conexaoStatusLabel.setForeground(new Color(0, 128, 0));
                        mostrarSucesso("Conexão estabelecida com sucesso!");
                        atualizarPastasServidor();
                        atualizarEstadoConexao();
                        conectarButton.setEnabled(true);
                    });
                }
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    conectado = false;
                    conexaoStatusLabel.setText("Erro na conexão");
                    conexaoStatusLabel.setForeground(Color.RED);
                    mostrarErro("Não foi possível conectar ao servidor.\nDetalhes: " + ex.getMessage());
                    atualizarEstadoConexao();
                    conectarButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void atualizarEstadoConexao() {
        uploadButton.setEnabled(conectado && pastaSelecionada != null);
        downloadButton.setEnabled(conectado);
        setTitle(conectado ? "Cliente FTP - Conectado a " + hostAtual + ":" + portaAtual : "Cliente FTP - Não conectado");
    }

    private JPanel createUploadPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Upload de pasta"));
        JButton selecionarPastaButton = new JButton("Selecionar Pasta");
        selecionarPastaButton.addActionListener(this::selecionarPasta);
        uploadButton = new JButton("Enviar para o servidor");
        uploadButton.setEnabled(false);
        uploadButton.addActionListener(this::uploadPasta);
        panel.add(selecionarPastaButton);
        panel.add(uploadButton);
        return panel;
    }

    private JPanel createServidorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Pastas no servidor"));
        listModel = new DefaultListModel<>();
        pastasServidorList = new JList<>(listModel);
        pastasServidorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(pastasServidorList);
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton refreshButton = new JButton("Atualizar lista");
        refreshButton.addActionListener(e -> {
            if (conectado) atualizarPastasServidor();
            else mostrarErro("Conecte-se ao servidor primeiro");
        });
        downloadButton = new JButton("Baixar pasta selecionada");
        downloadButton.addActionListener(this::downloadPasta);
        buttonPanel.add(refreshButton);
        buttonPanel.add(downloadButton);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Log de atividade"));
        logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Pronto para uso");
        panel.add(statusLabel);
        return panel;
    }

    private void selecionarPasta(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Selecione a pasta para enviar");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            pastaSelecionada = chooser.getSelectedFile();
            pastaSelecionadaId = FolderIdUtil.obterId(pastaSelecionada);
            uploadButton.setEnabled(true);
            statusLabel.setText("Pasta selecionada: " + pastaSelecionada.getName());
            gerarMensagemLog("Pasta selecionada: " + pastaSelecionada.getAbsolutePath());
        } catch (IOException ex) {
            mostrarErro("Erro ao gerar ID da pasta: " + ex.getMessage());
        }
    }

    private String gerarIdDaPasta(File pasta) throws IOException {
        return FolderIdUtil.obterId(pasta);
    }

    private void uploadPasta(ActionEvent e) {
        if (pastaSelecionada == null) {
            mostrarErro("Nenhuma pasta selecionada");
            return;
        }
        uploadButton.setEnabled(false);
        statusLabel.setText("Enviando pasta...");
        new Thread(() -> {
            try {
                executarUpload();
                SwingUtilities.invokeLater(() -> {
                    mostrarSucesso("Pasta enviada com sucesso.");
                    atualizarPastasServidor();
                    statusLabel.setText("Upload concluído.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> mostrarErro("Erro no upload: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> uploadButton.setEnabled(true));
            }
        }).start();
    }

    private void executarUpload() throws Exception {
        gerarMensagemLog("=== INICIANDO UPLOAD ===");
        try (Socket socket = new Socket(hostAtual, portaAtual);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

            socket.setSoTimeout(60000); // Timeout de 60 segundos
            entrada.readLine(); // 220 Servidor Pronto

            String infoPasta = pastaSelecionada.getName() + "_" + pastaSelecionadaId;
            saida.println("UPLOAD_FOLDER " + infoPasta);
            String resposta = entrada.readLine();
            if (resposta == null || !resposta.startsWith("150")) {
                throw new Exception("Servidor recusou o upload: " + resposta);
            }

            List<File> todosArquivos = coletarTodosArquivos(pastaSelecionada);
            enviarArquivosComConfirmacao(todosArquivos, socket, saida, entrada);

            saida.println("END_FOLDER");
            resposta = entrada.readLine();
            if (resposta == null || !resposta.startsWith("226")) {
                throw new Exception("Upload falhou na finalização: " + resposta);
            }
            gerarMensagemLog("=== UPLOAD CONCLUÍDO COM SUCESSO ===");
        }
    }

    private List<File> coletarTodosArquivos(File pastaRaiz) {
        List<File> arquivos = new ArrayList<>();
        coletarArquivosRecursivamente(pastaRaiz, arquivos);
        arquivos.sort(Comparator.comparing(f -> pastaRaiz.toPath().relativize(f.toPath()).toString()));
        return arquivos;
    }

    private void coletarArquivosRecursivamente(File pasta, List<File> arquivos) {
        File[] conteudo = pasta.listFiles();
        if (conteudo == null) return;
        Arrays.sort(conteudo, Comparator.comparing(File::getName));
        for (File item : conteudo) if (item.isFile()) arquivos.add(item);
        for (File item : conteudo) if (item.isDirectory()) coletarArquivosRecursivamente(item, arquivos);
    }

    private void enviarArquivosComConfirmacao(List<File> arquivos, Socket socket, PrintWriter saida, BufferedReader entrada) throws Exception {
        for (int i = 0; i < arquivos.size(); i++) {
            File arquivo = arquivos.get(i);
            String caminhoRelativo = pastaSelecionada.toPath().relativize(arquivo.toPath()).toString().replace("\\", "/");
            final int progresso = i + 1;
            SwingUtilities.invokeLater(() -> statusLabel.setText("Enviando %d/%d: %s".formatted(progresso, arquivos.size(), caminhoRelativo)));

            saida.println("FILE:" + caminhoRelativo);
            saida.println(arquivo.length());

            try (FileInputStream fis = new FileInputStream(arquivo)) {
                fis.transferTo(socket.getOutputStream());
                socket.getOutputStream().flush();
            }

            String resposta = entrada.readLine();
            if (resposta == null || !resposta.equals("OK")) {
                throw new Exception("Falha na confirmação do arquivo " + caminhoRelativo + ". Resposta: " + resposta);
            }
            gerarMensagemLog("✓ Enviado: " + caminhoRelativo);
        }
    }

    private void atualizarPastasServidor() {
        new Thread(() -> {
            try {
                List<String> pastas = getPastasServidor();
                SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    pastas.forEach(listModel::addElement);
                    gerarMensagemLog("Lista de pastas atualizada.");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> mostrarErro("Erro ao atualizar lista: " + e.getMessage()));
            }
        }).start();
    }

    private List<String> getPastasServidor() throws Exception {
        List<String> pastas = new ArrayList<>();
        try (Socket socket = new Socket(hostAtual, portaAtual);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

            entrada.readLine(); // 220
            saida.println("LIST");
            String line = entrada.readLine(); // 150
            if (line == null || !line.startsWith("150")) throw new IOException("Falha ao listar pastas: " + line);

            while ((line = entrada.readLine()) != null && !line.startsWith("226")) {
                if (line.startsWith("PASTA:")) {
                    pastas.add(line.substring(6).trim());
                }
            }
        }
        return pastas;
    }

    private void downloadPasta(ActionEvent e) {
        String pastaServidor = pastasServidorList.getSelectedValue();
        if (pastaServidor == null) {
            mostrarErro("Selecione uma pasta para baixar");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Escolha onde salvar a pasta");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File localSalvamento = chooser.getSelectedFile();
        downloadButton.setEnabled(false);
        statusLabel.setText("Baixando pasta...");

        new Thread(() -> {
            try {
                executarDownload(pastaServidor, localSalvamento);
                SwingUtilities.invokeLater(() -> {
                    mostrarSucesso("Pasta baixada com sucesso.");
                    statusLabel.setText("Download concluído");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> mostrarErro("Erro no download: " + ex.getMessage()));
                ex.printStackTrace();
            } finally {
                SwingUtilities.invokeLater(() -> downloadButton.setEnabled(true));
            }
        }).start();
    }

    private void executarDownload(String pastaServidor, File localSalvamento) throws Exception {
        gerarMensagemLog("=== INICIANDO DOWNLOAD ===");
        String nomeOriginal = FolderIdUtil.extrairNomeOriginal(pastaServidor);
        File pastaAlvo = new File(localSalvamento, nomeOriginal);
        pastaAlvo.mkdirs();

        try (Socket socket = new Socket(hostAtual, portaAtual);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) {

            socket.setSoTimeout(300);
            entrada.readLine(); // 220 Servidor Pronto

            saida.println("DOWNLOAD_FOLDER " + pastaServidor);
            String resposta = entrada.readLine();
            if (resposta == null || !resposta.startsWith("150")) {
                throw new Exception("Servidor recusou o download: " + resposta);
            }

            // A lógica de recebimento agora conversa com o novo servidor "em tempo real".
            receberArquivosComConfirmacao(pastaAlvo, socket, entrada, saida);

            // A próxima linha DEVE ser a 226, pois a END_FOLDER foi consumida no loop.
            resposta = entrada.readLine();
            if (resposta == null || !resposta.startsWith("226")) {
                throw new Exception("Download falhou na finalização: " + resposta);
            }
            gerarMensagemLog("=== DOWNLOAD CONCLUÍDO COM SUCESSO ===");
        }
    }

    private void receberArquivosComConfirmacao(File pastaDestino, Socket socket, BufferedReader entrada, PrintWriter saida) throws IOException {
        String linha;
        while ((linha = entrada.readLine()) != null) {
            // O servidor agora controla o fim, não precisamos mais do END_FOLDER aqui.
            if (linha.startsWith("226")) {
                gerarMensagemLog("Servidor sinalizou fim da transferência.");
                break;
            }

            if (linha.equals("END_FOLDER")) {
                // Mensagem final antes do 226. Apenas saímos do loop para ler a confirmação final.
                break;
            }

            if (linha.startsWith("FILE:")) {
                String nomeArquivoRelativo = linha.substring(5);
                long tamanhoArquivo = Long.parseLong(entrada.readLine());

                final String nomeFinal = nomeArquivoRelativo;
                SwingUtilities.invokeLater(() -> statusLabel.setText("Baixando: " + nomeFinal));

                File arquivoAlvo = new File(pastaDestino, nomeArquivoRelativo);
                arquivoAlvo.getParentFile().mkdirs();

                try (FileOutputStream fos = new FileOutputStream(arquivoAlvo)) {
                    // Técnica mais robusta para transferir bytes
                    socket.getInputStream().transferTo(fos);
                }

                // Confirma que o arquivo foi recebido para o servidor enviar o próximo
                saida.println("OK");
                gerarMensagemLog("✓ Baixado: " + nomeArquivoRelativo);
            }
        }
    }

    private void gerarMensagemLog(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + LocalDateTime.now().format(formatter) + "] " + mensagem + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void mostrarErro(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
        gerarMensagemLog("ERRO: " + mensagem);
    }

    private void mostrarSucesso(String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        gerarMensagemLog("SUCESSO: " + mensagem);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> new Cliente().setVisible(true));
    }
}