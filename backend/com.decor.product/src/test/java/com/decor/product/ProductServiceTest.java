package com.decor.product;

import com.decor.common.dto.RoleDto;
import com.decor.common.security.PermissionArea;
import com.decor.common.servlet.security.CustomUserDetails;
import com.decor.product.dto.FullProductDto;
import com.decor.product.dto.ImageDto;
import com.decor.product.dto.MetaDataDto;
import com.decor.product.dto.NewProductDto;
import com.decor.product.entities.Product;
import com.decor.product.entities.ProductImage;
import com.decor.product.entities.ProductMetaData;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.factories.ProductFactory;
import com.decor.product.repositories.ProductImageRepository;
import com.decor.product.repositories.ProductMetaDataRepository;
import com.decor.product.repositories.ProductRepository;
import com.decor.product.services.CatalogCacheService;
import com.decor.product.services.ProductService;
import com.decor.product.services.StockService;
import com.decor.product.util.FileManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ProductImageRepository productImageRepository;

	@Mock
	private ProductMetaDataRepository productMetaDataRepository;

	@Mock
	private ProductFactory productFactory;

	@Mock
	private FileManager fileManager;

	@Mock
	private StockService stockService;

	@Mock
	private CatalogCacheService catalogCache;

	@InjectMocks
	private ProductService productService;


	@Test
	void getProductById_existingProduct_returnsFullProductDto() throws ApiException {
		Product product = product(10L, "Lampara", "decoracion", "Lampara de techo", 2);
		FullProductDto fullProductDto = fullProductDto(10L);

		when(productRepository.findById(10L)).thenReturn(Optional.of(product));
		when(productFactory.getFullProductInfo(product)).thenReturn(fullProductDto);

		FullProductDto result = productService.getProductById(10L);

		assertSame(fullProductDto, result);
	}

	@Test
	void getProductById_missingProduct_throwsApiException() {
		when(productRepository.findById(10L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> productService.getProductById(10L));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals("Product not found", exception.getMessage());
		verifyNoInteractions(productFactory);
	}

	@Test
	void deleteProductById_existingProduct_deletesAndReturnsTrue() throws ApiException {
		when(productRepository.existsById(10L)).thenReturn(true);

		boolean deleted = productService.deleteProductById(10L);

		assertTrue(deleted);
		verify(productRepository).deleteById(10L);
	}

	@Test
	void deleteProductById_missingProduct_throwsApiException() {
		when(productRepository.existsById(10L)).thenReturn(false);

		ApiException exception = assertThrows(ApiException.class, () -> productService.deleteProductById(10L));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals("Product not found", exception.getMessage());
		verify(productRepository, never()).deleteById(any());
	}

	@Test
	void getProductImages_returnsMappedDtos() throws ApiException {
		Product product = product(1L, "Mesa", "living", "Mesa de madera", 4);
		ProductImage firstImage = image(11L, product, "/images/a.jpg", 0);
		ProductImage secondImage = image(12L, product, "/images/b.jpg", 1);

		when(productImageRepository.findByProduct_Id(1L)).thenReturn(List.of(firstImage, secondImage));

		ArrayList<ImageDto> result = productService.getProductImages(1L);

		assertEquals(List.of(
				new ImageDto(11L, "/images/a.jpg", 0),
				new ImageDto(12L, "/images/b.jpg", 1)
		), result);
	}

	@Test
	void getProductMetaData_existingMetadata_returnsDto() throws ApiException {
		ProductMetaData metadata = new ProductMetaData();
		metadata.setMaterial("Madera");
		metadata.setColor("Natural");
		metadata.setStyle("Rustico");
		metadata.setHeight(2.5);
		metadata.setWidth(1.2);
		metadata.setLength(0.8);
		metadata.setWeight(15.4);

		when(productMetaDataRepository.findByProduct_Id(1L)).thenReturn(Optional.of(metadata));

		MetaDataDto result = productService.getProductMetaData(1L);

		assertEquals(new MetaDataDto("Madera", "Natural", "Rustico", 2.5, 1.2, 0.8, 15.4), result);
	}

	@Test
	void getProductMetaData_missingMetadata_returnsNull() throws ApiException {
		when(productMetaDataRepository.findByProduct_Id(1L)).thenReturn(Optional.empty());

		MetaDataDto result = productService.getProductMetaData(1L);

		assertNull(result);
	}

	@Test
	void deleteImageById_deletesFileAndReordersRemainingImages() throws ApiException {
		Product product = product(1L, "Mesa", "living", "Mesa de madera", 4);
		ProductImage imageToDelete = image(10L, product, "/images/delete.jpg", 2);
		ProductImage firstRemainingImage = image(11L, product, "/images/keep-1.jpg", 3);
		ProductImage secondRemainingImage = image(12L, product, "/images/keep-2.jpg", 4);
		List<ProductImage> remainingImages = new ArrayList<>(List.of(firstRemainingImage, secondRemainingImage));

		when(productImageRepository.findById(10L)).thenReturn(Optional.of(imageToDelete));
		when(productImageRepository.findByProduct_IdOrderByImageOrderAsc(1L)).thenReturn(remainingImages);

		boolean deleted = productService.deleteImageById(10L);

		assertTrue(deleted);
		verify(fileManager).deleteImage("/images/delete.jpg");
		verify(productImageRepository).delete(imageToDelete);
		verify(productImageRepository).saveAll(remainingImages);
		assertEquals(0, remainingImages.get(0).getImageOrder());
		assertEquals(1, remainingImages.get(1).getImageOrder());
	}

	@Test
	void getProductByName_existingProduct_returnsFullProductDto() throws ApiException {
		Product product = product(20L, "Espejo", "decoracion", "Espejo redondo", 3);
		FullProductDto fullProductDto = fullProductDto(20L);

		when(productRepository.findByName("Espejo")).thenReturn(Optional.of(product));
		when(productFactory.getFullProductInfo(product)).thenReturn(fullProductDto);

		FullProductDto result = productService.getProductByName("Espejo");

		assertSame(fullProductDto, result);
	}

	@Test
	void getProductByName_missingProduct_throwsApiException() {
		when(productRepository.findByName("Espejo")).thenReturn(Optional.empty());

		ApiException exception = assertThrows(ApiException.class, () -> productService.getProductByName("Espejo"));

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals("Product not found", exception.getMessage());
	}



	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	// Usuario logueado con la mascara de permisos que le da users
	private static void loggedInWith(int permissions) {
		CustomUserDetails user = new CustomUserDetails(1L, List.of(new RoleDto("EMPLEADO", permissions)));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
	}

	@Test
	void createProduct_sinPermisoPrecios_ignoraPrecioYCosto() throws ApiException {
		loggedInWith(PermissionArea.PRODUCTOS.bit());
		when(productRepository.existsByName("Silla")).thenReturn(false);
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
			Product p = invocation.getArgument(0);
			p.setId(99L);
			return p;
		});
		when(productRepository.findById(99L)).thenAnswer(invocation -> Optional.of(product(99L, "Silla", null, null, 0)));

		productService.createProduct(new NewProductDto("Silla", null, null, 0, 0, new BigDecimal("15000"), new BigDecimal("-1")));

		ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
		verify(productRepository).save(saved.capture());
		assertNull(saved.getValue().getPrice());
		// Ni siquiera se valida: un costo negativo no da error porque se ignora
		assertNull(saved.getValue().getCost());
	}

	@Test
	void createProduct_createsNewProductAndReturnsFullProductDto() throws ApiException {
		loggedInWith(PermissionArea.ALL);
		NewProductDto newProductDto = new NewProductDto("Silla", "living", "Silla moderna", 9, 2, new BigDecimal("15000.00"), new BigDecimal("9000.00"));
		Product savedProduct = product(99L, "Silla", "living", "Silla moderna", 9);
		FullProductDto fullProductDto = fullProductDto(99L);

		when(productRepository.existsByName("Silla")).thenReturn(false);
		when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
			Product productToSave = invocation.getArgument(0);
			savedProduct.setName(productToSave.getName());
			savedProduct.setCategory(productToSave.getCategory());
			savedProduct.setDescription(productToSave.getDescription());
			savedProduct.setStock(productToSave.getStock());
			savedProduct.setMinStock(productToSave.getMinStock());
			savedProduct.setPrice(productToSave.getPrice());
			savedProduct.setCost(productToSave.getCost());
			savedProduct.setSlug(productToSave.getSlug());
			return savedProduct;
		});
		when(productRepository.findById(99L)).thenReturn(Optional.of(savedProduct));
		when(productFactory.getFullProductInfo(savedProduct)).thenReturn(fullProductDto);

		FullProductDto result = productService.createProduct(newProductDto);

		assertSame(fullProductDto, result);
		assertEquals("Silla", savedProduct.getName());
		assertEquals("living", savedProduct.getCategory());
		assertEquals("Silla moderna", savedProduct.getDescription());
		assertEquals(9, savedProduct.getStock());
		assertEquals(2, savedProduct.getMinStock());
		assertEquals(new BigDecimal("15000.00"), savedProduct.getPrice());
		assertEquals(new BigDecimal("9000.00"), savedProduct.getCost());
		verify(productRepository).existsByName("Silla");
		verify(stockService).registerInitialStock(savedProduct);
		// Slug para el link del catalogo
		assertEquals("silla", savedProduct.getSlug());
		verify(catalogCache).invalidate();
	}

	@Test
	void createProduct_duplicateName_throwsApiException() {
		when(productRepository.existsByName("Silla")).thenReturn(true);

		ApiException exception = assertThrows(
				ApiException.class,
				() -> productService.createProduct(new NewProductDto("Silla", "living", "Silla moderna", 9, 2, new BigDecimal("15000.00"), new BigDecimal("9000.00")))
		);

		assertEquals(HttpStatus.CONFLICT, exception.getStatus());
		assertEquals("The Product name is already in use", exception.getMessage());
		verifyNoInteractions(productFactory);
	}

	@Test
	void updateMetadata_existingMetadata_updatesFieldsAndReturnsFullProductDto() throws ApiException {
		Product product = product(40L, "Mesa", "living", "Mesa antigua", 2);
		ProductMetaData metadata = new ProductMetaData();
		metadata.setId(7L);
		metadata.setProduct(product);
		FullProductDto fullProductDto = fullProductDto(40L);
		MetaDataDto updatedData = new MetaDataDto("Madera", "Blanco", "Nordico", 2.1, 1.0, 0.7, 12.5);

		when(productMetaDataRepository.findByProduct_Id(40L)).thenReturn(Optional.of(metadata));
		when(productFactory.getFullProductInfo(product)).thenReturn(fullProductDto);
		when(productMetaDataRepository.save(any(ProductMetaData.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findById(40L)).thenReturn(Optional.of(product));
		FullProductDto result = productService.updateMetadata(40L, updatedData);

		assertSame(fullProductDto, result);
		assertEquals("Madera", metadata.getMaterial());
		assertEquals("Blanco", metadata.getColor());
		assertEquals("Nordico", metadata.getStyle());
		assertEquals(2.1, metadata.getHeight());
		assertEquals(1.0, metadata.getWidth());
		assertEquals(0.7, metadata.getLength());
		assertEquals(12.5, metadata.getWeight());
		verify(productMetaDataRepository).save(metadata);
	}

	@Test
	void updateMetadata_missingMetadataCreatesNewMetadataLinkedToProduct() throws ApiException {
		Product product = product(41L, "Silla", "living", "Silla moderna", 6);
		FullProductDto fullProductDto = fullProductDto(41L);
		MetaDataDto updatedData = new MetaDataDto("Metal", "Negro", "Industrial", 1.2, 0.6, 0.6, 8.0);

		when(productMetaDataRepository.findByProduct_Id(41L)).thenReturn(Optional.empty());
		when(productRepository.findById(41L)).thenReturn(Optional.of(product));
		when(productFactory.getFullProductInfo(product)).thenReturn(fullProductDto);
		when(productMetaDataRepository.save(any(ProductMetaData.class))).thenAnswer(invocation -> invocation.getArgument(0));

		FullProductDto result = productService.updateMetadata(41L, updatedData);

		assertSame(fullProductDto, result);
		ArgumentCaptor<ProductMetaData> metadataCaptor = ArgumentCaptor.forClass(ProductMetaData.class);
		verify(productMetaDataRepository).save(metadataCaptor.capture());

		ProductMetaData savedMetadata = metadataCaptor.getValue();
		assertSame(product, savedMetadata.getProduct());
		assertEquals("Metal", savedMetadata.getMaterial());
		assertEquals("Negro", savedMetadata.getColor());
		assertEquals("Industrial", savedMetadata.getStyle());
		assertEquals(1.2, savedMetadata.getHeight());
		assertEquals(0.6, savedMetadata.getWidth());
		assertEquals(0.6, savedMetadata.getLength());
		assertEquals(8.0, savedMetadata.getWeight());
	}

	@Test
	void updateMetadata_missingProduct_throwsApiException() {
		when(productMetaDataRepository.findByProduct_Id(41L)).thenReturn(Optional.empty());
		when(productRepository.findById(41L)).thenReturn(Optional.empty());

		ApiException exception = assertThrows(
				ApiException.class,
				() -> productService.updateMetadata(41L, new MetaDataDto("Metal", "Negro", "Industrial", 1.2, 0.6, 0.6, 8.0))
		);

		assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
		assertEquals("Product not found", exception.getMessage());
	}


	@Test
	void saveImages_savesFilesAndPersistsGeneratedPaths() throws ApiException {
		Product product = product(60L, "Banco", "living", "Banco auxiliar", 1);
		List<MultipartFile> files = List.of(org.mockito.Mockito.mock(MultipartFile.class), org.mockito.Mockito.mock(MultipartFile.class));
		List<ProductImage> finalImages = List.of(
				image(5L, product, "/generated/one.jpg", 0),
				image(6L, product, "/generated/two.jpg", 1)
		);

		when(fileManager.SaveAllImages(files)).thenReturn(List.of("/generated/one.jpg", "/generated/two.jpg"));
		when(productRepository.findById(60L)).thenReturn(Optional.of(product));
		when(productImageRepository.findByProduct_Id(60L)).thenReturn(List.of()).thenReturn(finalImages);

		boolean result = productService.saveImages(60L, files);

		assertTrue(result);
		verify(fileManager).SaveAllImages(files);
		verify(productImageRepository, times(2)).save(any(ProductImage.class));
	}

	private static Product product(Long id, String name, String category, String description, int stock) {
		Product product = new Product();
		product.setId(id);
		product.setName(name);
		product.setCategory(category);
		product.setDescription(description);
		product.setStock(stock);
		return product;
	}

	private static ProductImage image(Long id, Product product, String path, int order) {
		ProductImage image = new ProductImage();
		image.setId(id);
		image.setProduct(product);
		image.setPath(path);
		image.setImageOrder(order);
		return image;
	}

	private static FullProductDto fullProductDto(Long id) {
		FullProductDto fullProductDto = new FullProductDto();
		fullProductDto.setId(id);
		fullProductDto.setName("Producto " + id);
		fullProductDto.setCategory("Categoria " + id);
		fullProductDto.setDescription("Descripcion " + id);
		fullProductDto.setStock(id.intValue());
		fullProductDto.setMetadata(new MetaDataDto("Madera", "Natural", "Rustico", 1.0, 2.0, 3.0, 4.0));
		fullProductDto.setImages(List.of(new ImageDto(id, "/images/" + id + ".jpg", 0)));
		return fullProductDto;
	}
}
