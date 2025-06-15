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
        /* Coloca host e porta padrões de início */
        hostAtual = HOST;
        portaAtual = PORTA;

        inicializarGUI();
        //atualizarPastasServidor();
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

    private JPanel createConexaoPanel () {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(new TitledBorder("Configuração do servidor"));

        /* Campo para IP */
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
        conectarButton.setToolTipText("Testar conexão com o servidor e aplicar configurações");
        panel.add(conectarButton);

        conexaoStatusLabel = new JLabel("Não conectado");
        conexaoStatusLabel.setForeground(Color.RED);
        panel.add(conexaoStatusLabel);

        return panel;
    }

    private void testarConexao (ActionEvent e) {
        String novoHost = hostField.getText().trim();
        String portaTexto = portaField.getText().trim();

        /* Validação dos campos */
        if (novoHost.isEmpty()) {
            mostrarErro("Por favor, digite o endereço do servidor");
            hostField.requestFocus();
            return;
        }

        int novaPorta;
        try {
            novaPorta = Integer.parseInt(portaTexto);
            if (novaPorta < 1 || novaPorta > 65535) {
                throw new NumberFormatException("Porta fora dos limites");
            }
        }
        catch (NumberFormatException ex) {
            mostrarErro("Porta inválida. Digite um número entre 1 e 65535");
            portaField.requestFocus();
            return;
        }

        /* Testa a conexão em uma nova thread para não travar a aplicação */
        conectarButton.setEnabled(false);
        conexaoStatusLabel.setText("Testando...");
        conexaoStatusLabel.setForeground(Color.ORANGE);

        new Thread(() -> {
            boolean sucesso = false;
            String mensagemErro = "";

            try {
                gerarMensagemLog("Testando conexão com " + novoHost + ": " + novaPorta);

                /* Tenta uma conexão simples para ver se o servidor responde */
                try (Socket socket = new Socket(novoHost, novaPorta)) {
                    BufferedReader entrada =  new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    socket.setSoTimeout(50000); // Timeout de 5 segundos para o teste
                    String resposta = entrada.readLine();

                    if (resposta != null) {
                        sucesso = true;
                        gerarMensagemLog("Conexão bem-sucedida. Resposta: " + resposta);
                    }
                }
            }
            catch (IOException ex) {
                mensagemErro = ex.getMessage();
                gerarMensagemLog("Erro na conexão: " + ex.getMessage());
            }

            final boolean conexaoSucesso = sucesso;
            final String erro = mensagemErro;

            SwingUtilities.invokeLater(() -> {
                conectarButton.setEnabled(true);

                if (conexaoSucesso) {
                    hostAtual = novoHost;
                    portaAtual = novaPorta;
                    conectado = true;

                    conexaoStatusLabel.setText("Conectado (" + hostAtual + ":" + portaAtual + ")");
                    conexaoStatusLabel.setForeground(new Color(0, 128, 0));

                    mostrarSucesso("Conexão estabelecida com sucesso!\nServidor: " + hostAtual + ":" + portaAtual);

                    atualizarPastasServidor();
                }
                else {
                    conectado = false;
                    conexaoStatusLabel.setText("Erro na conexão");
                    conexaoStatusLabel.setForeground(Color.RED);

                    mostrarErro("Não foi possível conectar ao servidor.\nDetalhes: " + erro);
                }

                atualizarEstadoConexao();
            });
        }).start();
    }

    private void atualizarEstadoConexao () {
        /* O upload só funciona se há conexão e pasta selecionada */
        uploadButton.setEnabled(conectado && pastaSelecionada != null);

        /* O botão de download funciona a depender da conexão */
        downloadButton.setEnabled(conectado);

        /* Atualiza o título da janela */
        if (conectado) {
            setTitle("Cliente FTP - Conectado a " + hostAtual + ":" + portaAtual);
        }
        else {
            setTitle("Cliente FTP - Não conectado");
        }
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

        /* Lista de pastas no servidor */
        listModel = new DefaultListModel<>();
        pastasServidorList = new JList<>(listModel);
        pastasServidorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(pastasServidorList);
        scrollPane.setPreferredSize(new Dimension(400, 200));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton refreshButton = new JButton("Atualizar lista");
        refreshButton.addActionListener(e -> {
            if (conectado) {
                atualizarPastasServidor();
            }
            else {
                mostrarErro("Conecte-se ao servidor primeiro");
            }
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

        logArea = new JTextArea(10, 30);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

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

    private void selecionarPasta(ActionEvent e) throws UncheckedIOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Selecione a pasta para sincronizar");

        int resultado = chooser.showOpenDialog(this);

        if (resultado != JFileChooser.CANCEL_OPTION) {
            return;
        }

        try {
            pastaSelecionada = chooser.getSelectedFile();
            pastaSelecionadaId = gerarIdDaPasta(pastaSelecionada);

            uploadButton.setEnabled(true);
            statusLabel.setText("Pasta selecionada: " + pastaSelecionada.getName());

            gerarMensagemLog("Pasta selecionada: " + pastaSelecionada.getAbsolutePath());
            gerarMensagemLog("ID gerado para a pasta: " + pastaSelecionadaId);
        }
        catch (IOException ex) {
            mostrarErro("Erro ao selecionar pasta: " + ex.getMessage());
        }
    }

    private String gerarIdDaPasta (File pasta) throws IOException {
        return FolderIdUtil.obterId(pasta);
    }

    private void uploadPasta(ActionEvent e) {
        if (pastaSelecionada == null) {
            mostrarErro("Nenhuma pasta selecionada");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            uploadButton.setEnabled(false);
            statusLabel.setText("Enviando pasta...");
        });

        new Thread(() -> {
            try {
                executarUpload();
                SwingUtilities.invokeLater(() -> {
                    mostrarSucesso("Pasta enviada com sucesso.");
                    atualizarPastasServidor();
                    uploadButton.setEnabled(true);
                    statusLabel.setText("Upload concluído.");
                });
            }
            catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    mostrarErro("Erro no upload: " + ex.getMessage());
                    uploadButton.setEnabled(true);
                    statusLabel.setText("Erro no upload.");
                });
                gerarMensagemLog("ERRO: " + ex.getMessage());
            }
        }).start();
    }

    private void executarUpload() {
        gerarMensagemLog("=== INICIANDO UPLOAD ===");
        gerarMensagemLog("Conectando ao servidor " + hostAtual + ":" + portaAtual);

        try (Socket socket = new Socket(hostAtual, portaAtual);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), true)) { // AUTO-FLUSH habilitado

            // Configurações importantes do socket para evitar problemas de timing
            socket.setTcpNoDelay(true); // Envia dados imediatamente, sem buffer
            socket.setSoTimeout(30000); // Timeout de 30 segundos para leitura

            gerarMensagemLog("Socket configurado - TCP_NODELAY: true, SO_TIMEOUT: 30s");

            String resposta = entrada.readLine();
            gerarMensagemLog("SERVIDOR (conexão): " + resposta);

            String infoPasta = pastaSelecionada.getName() + "|" + pastaSelecionadaId;
            gerarMensagemLog("ENVIANDO: CHECK_FOLDER " + infoPasta);
            saida.println("CHECK_FOLDER " + infoPasta);
            // Removido flush manual porque PrintWriter está com auto-flush

            resposta = entrada.readLine();
            gerarMensagemLog("SERVIDOR (check): " + resposta);

            gerarMensagemLog("ENVIANDO: UPLOAD_FOLDER " + infoPasta);
            saida.println("UPLOAD_FOLDER " + infoPasta);

            resposta = entrada.readLine();
            gerarMensagemLog("SERVIDOR (upload): " + resposta);

            if (resposta.startsWith("150")) {
                // Primeiro, vamos coletar e contar todos os arquivos
                List<File> todosArquivos = coletarTodosArquivos(pastaSelecionada);
                gerarMensagemLog("=== INICIANDO ENVIO DE " + todosArquivos.size() + " ARQUIVOS ===");

                // ESTRATÉGIA CONSERVADORA: Um arquivo por vez, com confirmação
                enviarArquivosComConfirmacao(todosArquivos, socket, saida, entrada);

                gerarMensagemLog("ENVIANDO: END_FOLDER");
                saida.println("END_FOLDER");

                resposta = entrada.readLine();
                gerarMensagemLog("SERVIDOR (fim): " + resposta);

                if (!resposta.startsWith("226")) {
                    throw new Exception("Upload falhou - resposta final: " + resposta);
                }

                gerarMensagemLog("=== UPLOAD CONCLUÍDO COM SUCESSO ===");
            }
            else {
                throw new Exception("Servidor recusou o upload: " + resposta);
            }
        } catch (Exception e) {
            gerarMensagemLog("ERRO CRÍTICO: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Coleta todos os arquivos mantendo uma ordem determinística
     * Isso evita problemas de ordem que podem confundir o servidor
     */
    private List<File> coletarTodosArquivos(File pastaRaiz) {
        List<File> arquivos = new ArrayList<>();
        coletarArquivosRecursivamente(pastaRaiz, arquivos);

        // IMPORTANTE: Ordenar os arquivos por caminho para garantir ordem consistente
        arquivos.sort((f1, f2) -> {
            String path1 = pastaRaiz.toPath().relativize(f1.toPath()).toString();
            String path2 = pastaRaiz.toPath().relativize(f2.toPath()).toString();
            return path1.compareTo(path2);
        });

        gerarMensagemLog("Arquivos coletados e ordenados:");
        for (int i = 0; i < Math.min(5, arquivos.size()); i++) {
            String caminho = pastaRaiz.toPath().relativize(arquivos.get(i).toPath()).toString();
            gerarMensagemLog("  " + (i+1) + ". " + caminho);
        }
        if (arquivos.size() > 5) {
            gerarMensagemLog("  ... e mais " + (arquivos.size() - 5) + " arquivos");
        }

        return arquivos;
    }

    private void coletarArquivosRecursivamente(File pasta, List<File> arquivos) {
        File[] conteudo = pasta.listFiles();
        if (conteudo == null) {
            gerarMensagemLog("AVISO: Não foi possível listar conteúdo de " + pasta.getAbsolutePath());
            return;
        }

        // Primeiro todos os arquivos da pasta atual
        Arrays.sort(conteudo, Comparator.comparing(File::getName)); // Ordem alfabética

        for (File item : conteudo) {
            if (item.isFile()) {
                arquivos.add(item);
            }
        }

        // Depois as subpastas, recursivamente
        for (File item : conteudo) {
            if (item.isDirectory()) {
                coletarArquivosRecursivamente(item, arquivos);
            }
        }
    }

    /**
     * NOVA ESTRATÉGIA: Envio extremamente conservador
     * Um arquivo por vez, com confirmação obrigatória antes do próximo
     * Inclui delays estratégicos para evitar sobrecarregar o servidor
     */
    private void enviarArquivosComConfirmacao(List<File> arquivos, Socket socket,
                                              PrintWriter saida, BufferedReader entrada) throws Exception {

        int totalArquivos = arquivos.size();
        int sucessos = 0;

        for (int i = 0; i < totalArquivos; i++) {
            File arquivo = arquivos.get(i);
            String caminhoRelativo = pastaSelecionada.toPath().relativize(arquivo.toPath()).toString();

            gerarMensagemLog("=== ARQUIVO " + (i+1) + "/" + totalArquivos + " ===");
            gerarMensagemLog("Preparando: " + caminhoRelativo + " (" + arquivo.length() + " bytes)");

            // Atualiza interface
            final int arquivoAtual = i + 1;
            SwingUtilities.invokeLater(() -> statusLabel.setText("Enviando %d/%d: %s".formatted(arquivoAtual, totalArquivos, caminhoRelativo)));

            // PASSO 1: Enviar metadados do arquivo
            gerarMensagemLog("ENVIANDO: FILE:" + caminhoRelativo);
            saida.println("FILE:" + caminhoRelativo);

            gerarMensagemLog("ENVIANDO: " + arquivo.length());
            saida.println(arquivo.length());

            // DELAY ESTRATÉGICO: Dar tempo para o servidor processar os metadados
            Thread.sleep(100); // 100ms pode fazer toda a diferença

            // PASSO 2: Enviar conteúdo do arquivo em chunks pequenos
            long bytesEnviados = 0;
            try (FileInputStream fis = new FileInputStream(arquivo);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {

                byte[] buffer = new byte[4096]; // Buffer menor para melhor controle
                int bytesLidos;

                while ((bytesLidos = bis.read(buffer)) != -1) {
                    socket.getOutputStream().write(buffer, 0, bytesLidos);
                    bytesEnviados += bytesLidos;

                    // Flush a cada chunk para garantir envio imediato
                    socket.getOutputStream().flush();

                    // Log de progresso para arquivos grandes (> 1MB)
                    if (arquivo.length() > 1024 * 1024 && bytesEnviados % (256 * 1024) == 0) {
                        double progresso = (double) bytesEnviados / arquivo.length() * 100;
                        gerarMensagemLog("Progresso: " + String.format("%.1f%%", progresso));
                    }
                }
            }

            gerarMensagemLog("Conteúdo enviado: " + bytesEnviados + " bytes");

            // PASSO 3: Aguardar confirmação do servidor
            gerarMensagemLog("Aguardando confirmação...");
            String resposta = entrada.readLine();

            if ("OK".equals(resposta)) {
                sucessos++;
                gerarMensagemLog("✓ CONFIRMADO: " + caminhoRelativo);
            } else {
                gerarMensagemLog("✗ FALHA: " + caminhoRelativo + " - Resposta: '" + resposta + "'");
                throw new Exception("Erro ao enviar arquivo " + caminhoRelativo + " - Resposta do servidor: '" + resposta + "'");
            }

            // DELAY ENTRE ARQUIVOS: Evita sobrecarregar o servidor
            if (i < totalArquivos - 1) { // Não esperar após o último arquivo
                Thread.sleep(50); // 50ms entre arquivos
            }
        }

        gerarMensagemLog("=== RESUMO: " + sucessos + "/" + totalArquivos + " arquivos enviados com sucesso ===");
    }

    /* Atualiza a lista de pastas que estão disponíveis no servidor */
    private void atualizarPastasServidor () {
        new Thread(() -> {
            try {
                List<String> pastas = getPastasServidor();
                SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    for (String pasta : pastas) {
                        listModel.addElement(pasta);
                    }
                    gerarMensagemLog("Lista de pastas atualizada: " + pastas.size() + " itens");
                });
            }
            catch (Exception e) {
                SwingUtilities.invokeLater(() -> mostrarErro("Erro ao atualizar lista: %s".formatted(e.getMessage())));
                gerarMensagemLog("ERRO ao atualizar lista: " + e.getMessage());
            }
        }).start();
    }

    private List<String> getPastasServidor() throws Exception {
        List<String> pastas = new ArrayList<>();

        try (Socket socket = new Socket(hostAtual, portaAtual);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), false)) {

            // 1) Se o seu servidor envia um greeting (220, 200, etc.), leia-o:
            String line = entrada.readLine();
            gerarMensagemLog("Greeting: " + line);

            // 2) Peça a lista
            saida.println("LIST");
            saida.flush();

            // 3) Leia o código de início (esperamos algo como "150 ...")
            line = entrada.readLine();
            gerarMensagemLog("Resposta LIST início: " + line);
            if (line == null || !line.startsWith("150")) {
                throw new Exception("Não foi possível iniciar listagem de pastas: " + line);
            }

            // 4) Agora, leia tudo até o “226” de fim de listagem
            while ((line = entrada.readLine()) != null) {
                gerarMensagemLog("Listando: " + line);

                if (line.startsWith("226")) {
                    // terminou
                    break;
                }
                if (line.startsWith("FOLDER:") || line.startsWith("PASTA:")) {
                    String[] parts = line.split(":", 2);
                    pastas.add(parts[1].trim());
                }
            }
        }

        return pastas;
    }


    /* Função para download de pastas no servidor */
    private void downloadPasta (ActionEvent e) {
        String pastaSelecionada = pastasServidorList.getSelectedValue();
        if (pastaSelecionada == null) {
            mostrarErro("Selecione uma pasta para baixar");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Escolha onde salvar a pasta");

        int resultado = chooser.showSaveDialog(this);
        if (resultado != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File localSalvamento = chooser.getSelectedFile();

        downloadButton.setEnabled(false);
        statusLabel.setText("Baixando pasta...");

        new Thread(() -> {
            try {
                executarDownload(pastaSelecionada, localSalvamento);
                SwingUtilities.invokeLater(() -> {
                    mostrarSucesso("Pasta baixada com sucesso.");
                    downloadButton.setEnabled(true);
                    statusLabel.setText("Download concluído");
                });
            }
            catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    mostrarErro("Erro no download: " + ex.getMessage());
                    downloadButton.setEnabled(true);
                    statusLabel.setText("Erro no download");
                });
                gerarMensagemLog("ERRO no download: " + ex.getMessage());
            }
        }).start();
    }

    private void executarDownload (String pasta, File localSalvamento) throws Exception {
        gerarMensagemLog("Iniciando download de: " + pasta);

        /* Pegando o nome original e o ID */
        String nomeOriginal = FolderIdUtil.extrairNomeOriginal(pasta);
        File pastaAlvo = new File(localSalvamento, nomeOriginal);

        /* Garantindo que a pasta alvo existe antes de começar o download */
        if (!pastaAlvo.exists() && !pastaAlvo.mkdirs()) {
            throw new Exception("Não foi possível criar o diretório: " + pastaAlvo.getAbsolutePath());
        }

        try (Socket socket = new Socket(hostAtual, portaAtual);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter saida = new PrintWriter(socket.getOutputStream(), false)) {

            entrada.readLine();

            saida.println("DOWNLOAD_FOLDER " + pasta);
            saida.flush();

            String resposta = entrada.readLine();
            gerarMensagemLog("Servidor: " + resposta);

            if (!resposta.startsWith("150")) {
                throw new Exception("Servidor recusou o download: " + resposta);
            }

            //receberArquivos(pasta, socket, entrada, saida);
            receberArquivos(pastaAlvo, socket, entrada, saida);

            resposta = entrada.readLine(); // END_FOLDER
            resposta = entrada.readLine(); // 226 Download concluído
            gerarMensagemLog("Download finalizado: " + resposta);
        }
    }

    /* Função para receber os arquivos do servidor durante o download */
    private void receberArquivos(File pastaDestino, Socket socket, BufferedReader entrada, PrintWriter saida) throws Exception {
        String linha;

        while ((linha = entrada.readLine()) != null) {
            if (linha.equals("END_FOLDER")) {
                break;
            }

            if (linha.startsWith("FILE:")) {
                String nomeArquivo = linha.substring(5);
                long tamanhoArquivo = Long.parseLong(entrada.readLine());

                gerarMensagemLog("Recebendo: " + nomeArquivo + " (" + tamanhoArquivo + " bytes)");

                // Criando o arquivo alvo dentro da pasta de destino
                File arquivoAlvo = new File(pastaDestino, nomeArquivo);

                // CORREÇÃO PRINCIPAL: Verificação segura antes de chamar mkdirs()
                File diretorioPai = arquivoAlvo.getParentFile();
                if (diretorioPai != null && !diretorioPai.exists()) {
                    if (!diretorioPai.mkdirs()) {
                        throw new Exception("Não foi possível criar o diretório: " + diretorioPai.getAbsolutePath());
                    }
                }

                // Recebendo o conteúdo do arquivo
                try (FileOutputStream fos = new FileOutputStream(arquivoAlvo)) {
                    byte[] buffer = new byte[1024];
                    long totalRecebido = 0;

                    while (totalRecebido < tamanhoArquivo) {
                        int bytesParaLer = (int)Math.min(buffer.length, tamanhoArquivo - totalRecebido);
                        int bytesLidos = socket.getInputStream().read(buffer, 0, bytesParaLer);

                        if (bytesLidos == -1) {
                            throw new Exception("Conexão perdida durante o download do arquivo: " + nomeArquivo);
                        }

                        fos.write(buffer, 0, bytesLidos);
                        totalRecebido += bytesLidos;
                    }
                }

                saida.println("OK");
                saida.flush();
            }
        }
    }

    private void gerarMensagemLog (String mensagem) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + LocalDateTime.now().format(formatter) + "] " + mensagem + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void mostrarErro (String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Erro", JOptionPane.ERROR_MESSAGE);
        gerarMensagemLog("ERRO: " + mensagem);
    }

    private void mostrarSucesso (String mensagem) {
        JOptionPane.showMessageDialog(this, mensagem, "Sucesso", JOptionPane.INFORMATION_MESSAGE);
        gerarMensagemLog("SUCESSO: " + mensagem);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
            System.out.println("Erro ao carregar look and feel: " + e.getMessage());
        }

        SwingUtilities.invokeLater(() -> new Cliente().setVisible(true));
    }
}
