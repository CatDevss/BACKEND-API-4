package catdevs.georuraldatahub.collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import catdevs.georuraldatahub.entity.Source;
import catdevs.georuraldatahub.service.*;
import catdevs.georuraldatahub.collection.CollectionRequest.Product;

class CollectionServiceTest {
    private final SourceService sources = mock(SourceService.class);
    private final DatasetService datasets = mock(DatasetService.class);
    private final UserService users = mock(UserService.class);
    private final DictionaryService dictionaries = mock(DictionaryService.class);
    private final PublicDataConnectors connectors = mock(PublicDataConnectors.class);
    private final FileService files = mock(FileService.class);
    private final CollectionService service = new CollectionService(sources,datasets,users,dictionaries,connectors,files);
    private final CollectionRequest request = new CollectionRequest(Product.IBGE_MUNICIPIOS,1L,2L,"2022","4106902",null,null);
    private void source(String url) {
        Source source = new Source(); source.setId(3L); source.setUrl(url);
        when(sources.findEntityById(3L)).thenReturn(source);
    }
    @Test void reusesExistingSourcesDictionaryAndUpload() throws Exception {
        source("https://www.ibge.gov.br/");
        var data = new CollectedData("file.geojson","geojson",new byte[]{1},List.of(),null);
        when(connectors.collect(request)).thenReturn(data);
        service.collect(3L,request);
        verify(datasets).findEntityById(1L); verify(users).findEntityById(2L);
        verify(dictionaries).findForValidation(3L, null);
        verify(files).uploadCollectedFile(data,1L,2L,3L,null, "IBGE_MUNICIPIOS");
    }
    @Test void collectsWithoutDatasetAndPassesNullOnlyToOptionalAssociation() throws Exception {
        source("https://www.ibge.gov.br/");
        var request = new CollectionRequest(Product.IBGE_MUNICIPIOS, null, 2L, "2022", "4106902", null, null);
        var data = new CollectedData("file.geojson", "geojson", new byte[]{1}, List.of(), null);
        when(connectors.collect(request)).thenReturn(data);
        service.collect(3L, request);
        verifyNoInteractions(datasets);
        verify(files).uploadCollectedFile(data, null, 2L, 3L, null, "IBGE_MUNICIPIOS");
    }
    @Test void scheduledDailyRequestCanOmitDataset() {
        var daily = new CollectionRequest(Product.INPE_QUEIMADAS, null, 2L, "D-1", null, null, null);
        assertNull(CollectionScheduler.resolved(daily).datasetId());
    }
    @Test void selectedDictionaryIsCheckedBeforeDownloadAndForwardedToIngestion() throws Exception {
        source("https://www.ibge.gov.br/");
        var chosen = new CollectionRequest(Product.IBGE_MUNICIPIOS, null, 2L, "2024", null, null, null, 77L);
        var data = new CollectedData("file.geojson", "geojson", new byte[]{1}, List.of(), null);
        when(connectors.collect(chosen)).thenReturn(data);
        service.collect(3L, chosen);
        var order = inOrder(dictionaries, connectors, files);
        order.verify(dictionaries).findForValidation(3L, 77L);
        order.verify(connectors).collect(chosen);
        order.verify(files).uploadCollectedFile(data, null, 2L, 3L, 77L, "IBGE_MUNICIPIOS");
    }
    @Test void dictionaryFromAnotherSourcePreventsDownload() {
        source("https://www.ibge.gov.br/");
        var chosen = new CollectionRequest(Product.IBGE_MUNICIPIOS, null, 2L, "2024", null, null, null, 77L);
        when(dictionaries.findForValidation(3L, 77L)).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT, "Dicionário de outra fonte."));
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> service.collect(3L, chosen)).getStatusCode().value());
        verifyNoInteractions(connectors, files);
    }
    @Test void resolvingScheduledDayPreservesDictionarySelection() {
        var daily = new CollectionRequest(Product.INPE_QUEIMADAS, null, 2L, "D-1", null, null, null, 77L);
        assertEquals(77L, CollectionScheduler.resolved(daily).dictionaryId());
    }
    @Test void rejectsWrongAgencyBeforeNetworkOrUpload() {
        source("https://www.inpe.br/");
        assertThrows(IllegalArgumentException.class, () -> service.collect(3L,request));
        verifyNoInteractions(connectors,files);
    }
    @Test void missingDictionaryPreventsDownload() {
        source("https://www.ibge.gov.br/");
        when(dictionaries.findForValidation(3L, null)).thenThrow(new IllegalArgumentException("Sem dicionário"));
        assertThrows(IllegalArgumentException.class, () -> service.collect(3L,request));
        verifyNoInteractions(connectors,files);
    }
    @Test void downloadFailureNeverUploadsPartialContent() throws Exception {
        source("https://www.ibge.gov.br/"); when(connectors.collect(request)).thenThrow(new IOException("timeout"));
        assertEquals(502,assertThrows(ResponseStatusException.class, () -> service.collect(3L,request)).getStatusCode().value());
        verifyNoInteractions(files);
    }
    @Test void verifiesAgencyDomainsWithoutTrustingSuffixSpoofing() {
        assertTrue(CollectionService.matchesAgency("https://www.gov.br/inpe/pt-br", Product.INPE_PRODES));
        assertFalse(CollectionService.matchesAgency("https://www.gov.br/ibama", Product.INPE_PRODES));
        assertFalse(CollectionService.matchesAgency("https://ibge.gov.br.evil.test",Product.IBGE_BIOMAS));
        assertFalse(CollectionService.matchesAgency("https://evil@www.ibge.gov.br",Product.IBGE_BIOMAS));
    }
    @Test void dailySchedulingResolvesYesterdayWhileKeepingExplicitAnnualPeriods() {
        var daily = new CollectionRequest(Product.INPE_QUEIMADAS,1L,2L,"D-1",null,null,null);
        var resolved = CollectionScheduler.resolved(daily);
        assertEquals(java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusDays(1).toString(),resolved.competence());
        assertSame(request,CollectionScheduler.resolved(request));
    }
}
