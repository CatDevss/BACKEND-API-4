package catdevs.georuraldatahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.entity.Dataset;
import catdevs.georuraldatahub.repository.DatasetRepository;
import catdevs.georuraldatahub.repository.FeatureRepository;

/**
 * Confere se o usuário associado à chave da API é o proprietário do conjunto
 */
@Service
public class IndicatorDatasetAccessService {

    private final DatasetRepository datasetRepository;
    private final FeatureRepository featureRepository;

    public IndicatorDatasetAccessService(
            DatasetRepository datasetRepository,
            FeatureRepository featureRepository) {
        this.datasetRepository = datasetRepository;
        this.featureRepository = featureRepository;
    }

    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade autenticada inválida.");
        }
    }

    public void requireFeatureAccess(Long featureId) {
        if (!featureRepository.existsByIdAndLayer_Dataset_User_Id(featureId, currentUserId())) {
            // Responde como não encontrado para não revelar feições de outros usuários.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feição não encontrada.");
        }
    }

    public void requireDatasetAccess(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto não encontrado."));
        requireOwner(dataset);
    }

    public void requireOwner(Dataset dataset) {
        Long authenticatedUserId = currentUserId();
        Long ownerId = dataset.getUser() == null ? null : dataset.getUser().getId();
        if (ownerId == null || !ownerId.equals(authenticatedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Você não possui permissão para acessar os indicadores deste conjunto.");
        }
    }
}