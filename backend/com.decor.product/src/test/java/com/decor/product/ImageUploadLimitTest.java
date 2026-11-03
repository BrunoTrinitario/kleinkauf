package com.decor.product;

import com.decor.common.audit.AuditLog;
import com.decor.common.servlet.exceptions.ApiException;
import com.decor.product.services.CatalogCacheService;
import com.decor.product.services.ProductService;
import com.decor.product.util.FileManager;
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

// El limite general de subida es 5 MB (por el CSV), pero las imagenes siguen en 1 MB
@ExtendWith(MockitoExtension.class)
class ImageUploadLimitTest {

	@Mock
	private FileManager fileManager;

	@Mock
	private CatalogCacheService catalogCache;
	@Mock
	private AuditLog auditLog;


	@InjectMocks
	private ProductService productService;

	@Test
	void imagenDeMasDe1MB_respondePayloadTooLargeYNoGuardaNinguna() {
		MockMultipartFile chica = new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[1024]);
		MockMultipartFile grande = new MockMultipartFile("files", "b.jpg", "image/jpeg", new byte[(int) ProductService.MAX_IMAGE_BYTES + 1]);

		ApiException ex = assertThrows(ApiException.class, () -> productService.saveImages(1L, List.of(chica, grande)));

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatus());
		verifyNoInteractions(fileManager);
	}
}
