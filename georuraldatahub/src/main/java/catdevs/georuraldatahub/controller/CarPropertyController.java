package catdevs.georuraldatahub.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import catdevs.georuraldatahub.security.Permissions;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import catdevs.georuraldatahub.service.CarPropertyService;

/** BE-15: consulta dos imóveis importados do CAR. */
@RestController
@RequestMapping("/imoveis")
public class CarPropertyController {
    private final CarPropertyService service;

    public CarPropertyController(CarPropertyService service) {
        this.service = service;
    }

    /** Lista paginada, sem geometria. Filtros: codigoCar, municipio, camada, conjunto. */
    @PreAuthorize(Permissions.MAP_READ)
    @GetMapping
    public Object list(@RequestParam(required = false) String codigoCar,
            @RequestParam(required = false) String municipio, @RequestParam(required = false) Long camada,
            @RequestParam(required = false) Long conjunto, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginacao invalida.");
        return service.list(codigoCar, municipio, camada, conjunto, page, size);
    }

    @PreAuthorize(Permissions.MAP_READ)
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String detail(@PathVariable long id) {
        return service.detail(id);
    }

    /** Geometria de um imóvel (GeoJSON Feature, EPSG:4326). */
    @PreAuthorize(Permissions.MAP_READ)
    @GetMapping(value = "/{id}/geometria", produces = MediaType.APPLICATION_JSON_VALUE)
    public String geometry(@PathVariable long id) {
        return service.geometry(id);
    }

    /** Vários imóveis de uma vez para o mapa (GeoJSON FeatureCollection, EPSG:4326). */
    @PreAuthorize(Permissions.MAP_READ)
    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public String geoJson(@RequestParam(required = false) String codigoCar,
            @RequestParam(required = false) String municipio, @RequestParam(required = false) Long camada,
            @RequestParam(required = false) Long conjunto, @RequestParam(defaultValue = "50") int limite) {
        return service.geoJson(codigoCar, municipio, camada, conjunto, limite);
    }
}