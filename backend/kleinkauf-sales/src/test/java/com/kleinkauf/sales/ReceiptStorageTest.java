package com.kleinkauf.sales;

import com.kleinkauf.sales.util.ReceiptStorage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptStorageTest {

	@TempDir
	Path dir;

	@Test
	void reconoceElTipoPorLosPrimerosBytes() {
		assertEquals(ReceiptStorage.FileType.JPG, ReceiptStorage.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
		assertEquals(ReceiptStorage.FileType.PNG, ReceiptStorage.detect(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));
		assertEquals(ReceiptStorage.FileType.PDF, ReceiptStorage.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
		assertNull(ReceiptStorage.detect("<svg></svg>".getBytes(StandardCharsets.US_ASCII)));
		assertNull(ReceiptStorage.detect(new byte[]{(byte) 0xFF}));
		assertNull(ReceiptStorage.detect(new byte[0]));
	}

	@Test
	void guardaPorTiendaYPedido_ySeLee() throws Exception {
		ReceiptStorage storage = storage();
		byte[] bytes = "%PDF-1.7 comprobante".getBytes(StandardCharsets.US_ASCII);

		String path = storage.save("tienda1", "K7QM-4X2P", bytes, ReceiptStorage.FileType.PDF);

		assertTrue(path.startsWith("tienda1/K7QM-4X2P/") && path.endsWith(".pdf"));
		assertArrayEquals(bytes, Files.readAllBytes(storage.load(path).getFile().toPath()));

		storage.deleteQuietly(path);
		assertFalse(storage.load(path).exists());
	}

	@Test
	void noSaleDeLaCarpeta() {
		ReceiptStorage storage = storage();
		assertThrows(IllegalArgumentException.class, () -> storage.load("../../etc/passwd"));
	}

	private ReceiptStorage storage() {
		ReceiptStorage storage = new ReceiptStorage();
		ReflectionTestUtils.setField(storage, "receiptsPath", dir.toString());
		return storage;
	}
}
