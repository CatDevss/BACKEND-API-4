Fixtures sintéticas geradas com GDAL 3.6.2 para testes locais.
SIRGAS 2000 / UTM 22S (EPSG:31982), polígono e grade no Paraná.
GeoPackage e Shapefile: atributo codarea=4106902. GeoTIFF: 8x6, uma banda Byte, NoData=255, célula de 30 metros.
Não representam limites oficiais ou dados ambientais medidos.
A fixture parana-geographic.tif é uma cópia reprojetada para EPSG:4326 pelo GDAL, com unidade de célula em graus.

O leitor atual dos testes é GeoTools (Java). GDAL descreve apenas como estas fixtures originais foram geradas; Python/GDAL não são necessários para executar a aplicação ou os testes.
