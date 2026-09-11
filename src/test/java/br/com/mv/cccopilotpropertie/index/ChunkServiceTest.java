package br.com.mv.cccopilotpropertie.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkServiceTest {

    private ChunkService chunkService;

    @BeforeEach
    void setUp() {
        chunkService = new ChunkService();
    }

    @Test
    void shouldRecognizeJavaRecordAsDto() {
        String recordCode = """
                package com.example.dto;
                
                import jakarta.validation.constraints.NotBlank;
                import jakarta.validation.constraints.NotNull;
                
                public record CreateUserRequest(
                    @NotNull Long id,
                    @NotBlank String username,
                    String email
                ) {}
                """;

        assertTrue(chunkService.isDto(recordCode));
        List<String> chunks = chunkService.chunk(recordCode);
        assertEquals(1, chunks.size(), "DTO deve ser mantido como chunk único");

        String chunk = chunks.get(0);
        assertTrue(chunk.contains("TIPO: DTO DE ENTRADA"));
        assertTrue(chunk.contains("NOME: CreateUserRequest"));
        assertTrue(chunk.contains("id (@NotNull)"));
        assertTrue(chunk.contains("username (@NotBlank)"));
    }

    @Test
    void shouldRecognizeCaseInsensitiveDtoNaming() {
        String dtoCode = """
                public class PatientDto {
                    private String name;
                }
                """;
        assertTrue(chunkService.isDto(dtoCode));

        String upperDto = """
                public class PatientDTO {
                    private String name;
                }
                """;
        assertTrue(chunkService.isDto(upperDto));

        String lowerDto = """
                public class patientdto {
                    private String name;
                }
                """;
        assertTrue(chunkService.isDto(lowerDto));
    }

    @Test
    void shouldExtractLombokAnnotationsInDto() {
        String code = """
                package com.example.dto;
                
                import lombok.Data;
                import lombok.Builder;
                import jakarta.validation.constraints.NotNull;
                
                @Data
                @Builder
                public class OrderDto {
                    @NotNull
                    private String orderId;
                }
                """;

        assertTrue(chunkService.isDto(code));
        List<String> chunks = chunkService.chunk(code);
        assertEquals(1, chunks.size());

        String chunk = chunks.get(0);
        assertTrue(chunk.contains("ANOTAÇÕES LOMBOK:"));
        assertTrue(chunk.contains("@Data"));
        assertTrue(chunk.contains("@Builder"));
        assertTrue(chunk.contains("orderId (@NotNull)"));
    }

    @Test
    void shouldNotTreatServiceOrControllerAsDto() {
        String serviceCode = """
                @Service
                public class UserService {
                    public void execute(@NotNull String param) {}
                }
                """;

        assertFalse(chunkService.isDto(serviceCode));
        assertTrue(chunkService.isService(serviceCode));

        String controllerCode = """
                @RestController
                public class UserController {
                    public void handle() {}
                }
                """;
        assertFalse(chunkService.isDto(controllerCode));
        assertTrue(chunkService.isController(controllerCode));
    }

    @Test
    void shouldEnrichControllerChunk() {
        String controllerCode = """
                package com.example.controller;
                
                import org.springframework.web.bind.annotation.RestController;
                
                @RestController
                public class OrderController {
                    public void getOrders() {}
                }
                """;

        List<String> chunks = chunkService.chunk(controllerCode);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("TIPO: CONTROLLER / API"));
        assertTrue(chunks.get(0).contains("NOME: OrderController"));
    }

    @Test
    void shouldCreateOverlappingChunksInFallback() {
        // Texto longo que excede 800 caracteres
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            sb.append("linha_").append(i).append(" = valor_").append(i).append(";\n");
        }
        String largeText = sb.toString();
        assertTrue(largeText.length() > 800);

        List<String> chunks = chunkService.chunkFallback(largeText);
        assertTrue(chunks.size() >= 2, "Texto grande deve ser dividido em chunks");

        // Verifica overlap entre o primeiro e o segundo chunk
        String chunk0 = chunks.get(0);
        String chunk1 = chunks.get(1);

        String chunk0End = chunk0.substring(chunk0.length() - 100);
        assertTrue(chunk1.contains(chunk0End), "O segundo chunk deve conter o final do primeiro chunk (overlap)");
    }

    @Test
    void shouldReturnEmptyListForBlankText() {
        assertTrue(chunkService.chunk("").isEmpty());
        assertTrue(chunkService.chunk("   ").isEmpty());
        assertTrue(chunkService.chunk(null).isEmpty());
    }
}
