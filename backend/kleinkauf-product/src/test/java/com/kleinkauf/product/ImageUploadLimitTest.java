package com.kleinkauf.product;

import com.kleinkauf.common.audit.AuditLog;
import com.kleinkauf.common.servlet.exceptions.ApiException;
import com.kleinkauf.product.services.CatalogCacheService;
import com.kleinkauf.product.repositories.ProductRepository;
import com.kleinkauf.product.services.ProductService;
import com.kleinkauf.product.util.FileManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// El limite general de subida es 5 MB (por el CSV), pero las imagenes siguen en 1 MB
@ExtendWith(MockitoExtension.class)
class ImageUploadLimitTest {

	@Mock
	private FileManager fileManager;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private CatalogCacheService catalogCache;
	@Mock
	private AuditLog auditLog;


	@InjectMocks
	private ProductService productService;

	@Test
	void imagenDeMasDe1MB_respondePayloadTooLargeYNoGuardaNinguna() {
		when(productRepository.existsById(1L)).thenReturn(true);
		MockMultipartFile chica = new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[1024]);
		MockMultipartFile grande = new MockMultipartFile("files", "b.jpg", "image/jpeg", new byte[(int) ProductService.MAX_IMAGE_BYTES + 1]);

		ApiException ex = assertThrows(ApiException.class, () -> productService.saveImages(1L, List.of(chica, grande)));

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatus());
		verifyNoInteractions(fileManager);
	}
}
