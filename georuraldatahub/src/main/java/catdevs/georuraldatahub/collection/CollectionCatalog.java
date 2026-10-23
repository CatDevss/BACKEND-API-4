package catdevs.georuraldatahub.collection;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CollectionCatalog {
    public record Entry(String id, String agency, String product, String access, String documentation, String limitations) {}
    public List<Entry> list() {
        return List.of(
            new Entry("IBGE_MUNICIPIOS", "IBGE", "Malha municipal do Paraná", "AUTOMATICO",
                "https://servicodados.ibge.gov.br/api/docs/malhas?versao=3",
                "Malha simplificada para agregação, não cadastro fundiário. Exige ano publicado pela API; 399 municípios paranaenses ou um código iniciado por 41."),
            new Entry("IBGE_BIOMAS", "IBGE", "Limites dos biomas no Paraná", "AUTOMATICO",
                "https://geoftp.ibge.gov.br/informacoes_ambientais/estudos_ambientais/biomas/vetores/",
                "Edições 2019 e 2025, escala 1:250.000, recortadas pelo limite IBGE do Paraná. Mantém coordenadas SIRGAS 2000; cálculo de área exige projeção equivalente posterior."),
            new Entry("INPE_PRODES", "INPE", "Incremento anual no Paraná, por bioma", "AUTOMATICO",
                "https://terrabrasilis.dpi.inpe.br/download-de-dados/",
                "WFS paginado com ano explícito e Cerrado ou Mata Atlântica, sempre recortado ao Paraná; bbox opcional só restringe mais. Limites operacionais abortam a coleta, sem salvar base truncada."),
            new Entry("INPE_QUEIMADAS", "INPE", "Focos diários no Paraná", "AUTOMATICO",
                "https://dataserver-coids.inpe.br/queimadas/queimadas/focos/csv/diario/Brasil/",
                "CSV diário nacional preservado, saída filtrada por estado_id=41 e município paranaense, todos os satélites; disponibilidade limitada à retenção do servidor. Focos não equivalem a área queimada."),
            new Entry("ICMBIO", "ICMBio", "UCs federais e embargos", "AGUARDA_HOMOLOGACAO",
                "https://www.gov.br/icmbio/pt-br/assuntos/dados_geoespaciais",
                "Página oficial publica WFS na INDE e downloads SHP. Selecionar produto/camada e homologar antes de ativar; upload manual disponível."),
            new Entry("MMA", "MMA", "Unidades de conservação do CNUC", "AGUARDA_HOMOLOGACAO",
                "https://dados.mma.gov.br/dataset/unidadesdeconservacao",
                "Recursos ZIP por edição, incluindo links SharePoint sujeitos a mudanças. Não tratar página HTML como dado; upload manual disponível."),
            new Entry("FUNAI", "FUNAI", "Terras indígenas", "AGUARDA_HOMOLOGACAO",
                "https://www.gov.br/funai/pt-br/atuacao/terras-indigenas/geoprocessamento-e-mapas/geprocessamento",
                "GeoServer e downloads oficiais com atualização mensal; distinguir áreas em estudo das demais fases. Upload manual disponível."),
            new Entry("INCRA", "INCRA", "Territórios e assentamentos", "AGUARDA_HOMOLOGACAO",
                "https://acervofundiario.incra.gov.br/acervo/login.php",
                "Portal informa autenticação gov.br. Acesso automatizado sem autenticação não foi confirmado; upload manual disponível."),
            new Entry("SFB", "SFB", "Cadastro Nacional de Florestas Públicas", "AGUARDA_HOMOLOGACAO",
                "https://dados.florestal.gov.br/pt_BR/dataset/cadastro-nacional-de-florestas-publicas-cnfp",
                "Catálogo publica recursos por edição e geosserviços; CSV estatístico não substitui polígonos. Upload manual disponível."),
            new Entry("IBAMA", "IBAMA", "Áreas embargadas", "AGUARDA_HOMOLOGACAO",
                "https://pamgia.ibama.gov.br/server/rest/services/01_Publicacoes_Bases/adm_embargos_ibama_a/OGCFeatureServer/collections?f=html",
                "Serviço OGC público documentado; homologar camada, vigência e paginação. Não coletar dados pessoais além do necessário; upload manual disponível."),
            new Entry("CAR_SICAR", "CAR/SICAR", "Imóvel, APP, reserva legal e vegetação declarada", "MANUAL",
                "https://www.car.gov.br/publico/",
                "Mantido upload existente; nenhuma automação de login, captcha ou download não autorizado.")
        );
    }
    public Entry entry(CollectionRequest.Product product) {
        return list().stream().filter(e -> e.id().equals(product.name())).findFirst().orElseThrow();
    }
}
