package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;

public class FolderIdUtil {
    // Cache simples para evitar recálculo: Path -> ID
    private static final ConcurrentHashMap<Path, String> cache = new ConcurrentHashMap<>();

    /**
     * Gera um ID único e consistente para uma pasta, de forma compatível com qualquer sistema operacional.
     * A estratégia prioriza o 'fileKey' do sistema de arquivos, que é um identificador único.
     * Se não estiver disponível, usa uma combinação de caminho, data e tamanho para criar um hash.
     *
     * @param pasta O diretório para o qual o ID será gerado.
     * @return Uma String que representa o ID único da pasta.
     * @throws IOException Se ocorrer um erro de I/O ao ler os atributos da pasta.
     */
    public static String obterId(File pasta) throws IOException {
        Path caminho = pasta.toPath();

        // Verifica o cache primeiro para otimizar o desempenho.
        if (cache.containsKey(caminho)) {
            return cache.get(caminho);
        }

        String id;
        try {
            // 1. Tenta usar o método padrão e mais confiável (fileKey)
            BasicFileAttributes attrs = Files.readAttributes(caminho, BasicFileAttributes.class);
            Object fileKey = attrs.fileKey();

            if (fileKey != null) {
                // Usa o hash do fileKey, que é a forma mais robusta e portável.
                id = Integer.toString(Math.abs(fileKey.hashCode()));
            } else {
                // 2. Se fileKey não for suportado, usa o método de fallback.
                id = gerarIdPorFallback(pasta);
            }
        } catch (Exception e) {
            // 3. Em caso de qualquer erro, usa o método de fallback como segurança.
            id = gerarIdPorFallback(pasta);
        }

        // Armazena o ID gerado no cache antes de retorná-lo.
        cache.put(caminho, id);
        return id;
    }

    /**
     * Método de fallback para gerar um ID quando o 'fileKey' não está disponível.
     * Cria um hash a partir do caminho absoluto, data da última modificação e tamanho total da pasta.
     *
     * @param pasta A pasta para a qual o ID será gerado.
     * @return Um ID em String baseado no hash dos atributos.
     */
    private static String gerarIdPorFallback(File pasta) {
        try {
            String caminhoAbsoluto = pasta.getAbsolutePath();
            long ultimaModificacao = pasta.lastModified();
            long tamanho = calcularTamanhoPasta(pasta);

            // Combina os atributos em uma única String para gerar o hash.
            String baseParaHash = caminhoAbsoluto + ultimaModificacao + tamanho;
            int hash = baseParaHash.hashCode();
            return String.valueOf(Math.abs(hash));
        } catch (Exception e) {
            // Como último recurso absoluto, usa o tempo atual para garantir um valor único.
            return String.valueOf(System.currentTimeMillis());
        }
    }

    /**
     * Extrai o nome original da pasta a partir de um nome combinado (nome_id).
     * @param nomeComId O nome da pasta no formato "nomeOriginal_id".
     * @return Apenas o nome original da pasta.
     */
    public static String extrairNomeOriginal(String nomeComId) {
        int ultimoUnderline = nomeComId.lastIndexOf('_');
        if (ultimoUnderline > 0) {
            // Verifica se o que vem depois do underline é numérico (para ser um ID)
            String supostoId = nomeComId.substring(ultimoUnderline + 1);
            if (supostoId.matches("\\d+")) {
                return nomeComId.substring(0, ultimoUnderline);
            }
        }
        return nomeComId; // Retorna o nome completo se não encontrar um ID no formato esperado
    }

    /**
     * Extrai o ID da pasta a partir de um nome combinado (nome_id).
     * @param nomeComId O nome da pasta no formato "nomeOriginal_id".
     * @return Apenas o ID da pasta, ou uma string vazia se não for encontrado.
     */
    public static String extrairId(String nomeComId) {
        int ultimoUnderline = nomeComId.lastIndexOf('_');
        if (ultimoUnderline > 0) {
            String supostoId = nomeComId.substring(ultimoUnderline + 1);
            // Garante que estamos extraindo algo que parece um ID
            if (supostoId.matches("\\d+")) {
                return supostoId;
            }
        }
        return ""; // Retorna vazio se não encontrar
    }


    /**
     * Calcula o tamanho total de uma pasta somando o tamanho de todos os arquivos internos, recursivamente.
     * Este método agora é PÚBLICO para ser usado pelo ClienteHandler.
     * @param pasta O diretório a ser medido.
     * @return O tamanho total da pasta em bytes.
     */
    public static long calcularTamanhoPasta(File pasta) { // <-- ALTERADO PARA PUBLIC
        long tamanho = 0;
        File[] arquivos = pasta.listFiles();
        if (arquivos != null) {
            for (File arquivo : arquivos) {
                if (arquivo.isDirectory()) {
                    tamanho += calcularTamanhoPasta(arquivo);
                } else {
                    tamanho += arquivo.length();
                }
            }
        }
        return tamanho;
    }
}