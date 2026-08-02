package kr.hs.gbsw.communication.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import kr.hs.gbsw.communication.SchoolCommunicationApplication;
import kr.hs.gbsw.communication.auth.service.OneTimeCodeGenerator;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import kr.hs.gbsw.communication.user.service.SuperAdminBootstrapService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class SuperAdminBootstrapCommand {

    private static final String LOGIN_ID_ENV = "BOOTSTRAP_LOGIN_ID";
    private static final String DISPLAY_NAME_ENV = "BOOTSTRAP_DISPLAY_NAME";
    private static final String OUTPUT_FILE_ENV = "BOOTSTRAP_OUTPUT_FILE";

    private SuperAdminBootstrapCommand() {
    }

    public static void main(String[] args) throws Exception {
        String loginId = requiredEnvironment(LOGIN_ID_ENV);
        String displayName = requiredEnvironment(DISPLAY_NAME_ENV);
        validateAccountInput(loginId, displayName);
        Path outputFile = Path.of(requiredEnvironment(OUTPUT_FILE_ENV)).toAbsolutePath().normalize();
        validateOutputPath(outputFile);

        boolean materialWritten = false;
        boolean bootstrapCommitted = false;
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SchoolCommunicationApplication.class)
                .run(bootstrapRuntimeArguments(args))) {
            Clock clock = context.getBean(Clock.class);
            ApplicationSecurityProperties properties = context.getBean(ApplicationSecurityProperties.class);
            OneTimeCodeGenerator generator = context.getBean(OneTimeCodeGenerator.class);
            SuperAdminBootstrapService service = context.getBean(SuperAdminBootstrapService.class);

            Instant now = clock.instant();
            Instant expiresAt = now.plus(properties.credentials().activationCodeTtl());
            String activationCode = generator.generate();
            writeMaterial(outputFile, loginId, activationCode, expiresAt);
            materialWritten = true;

            UUID publicId = service.bootstrap(
                    loginId,
                    displayName,
                    activationCode,
                    expiresAt,
                    now,
                    "bootstrap-" + UUID.randomUUID());
            bootstrapCommitted = true;
            System.out.printf(
                    "Initial super administrator created: publicId=%s, activation material=%s%n",
                    publicId,
                    outputFile);
        } finally {
            if (materialWritten && !bootstrapCommitted) {
                Files.deleteIfExists(outputFile);
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String[] bootstrapRuntimeArguments(String[] originalArguments) {
        return Stream.concat(
                        Arrays.stream(originalArguments),
                        Stream.of(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--springdoc.api-docs.enabled=false",
                                "--springdoc.swagger-ui.enabled=false"))
                .toArray(String[]::new);
    }

    private static void validateOutputPath(Path outputFile) {
        Path parent = outputFile.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("BOOTSTRAP_OUTPUT_FILE parent directory must already exist");
        }
        if (Files.exists(outputFile)) {
            throw new IllegalArgumentException("BOOTSTRAP_OUTPUT_FILE must not already exist");
        }
    }

    private static void validateAccountInput(String loginId, String displayName) {
        if (!loginId.matches("[A-Za-z0-9._-]{3,100}")) {
            throw new IllegalArgumentException("BOOTSTRAP_LOGIN_ID has an invalid format");
        }
        int displayNameLength = displayName.codePointCount(0, displayName.length());
        boolean hasControlCharacter = displayName.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT);
        if (displayName.isBlank() || displayNameLength > 100 || hasControlCharacter) {
            throw new IllegalArgumentException("BOOTSTRAP_DISPLAY_NAME has an invalid format");
        }
    }

    private static void writeMaterial(
            Path outputFile,
            String loginId,
            String activationCode,
            Instant expiresAt
    ) throws IOException {
        FileStore fileStore = Files.getFileStore(outputFile.getParent());
        if (!fileStore.supportsFileAttributeView("posix")) {
            throw new IOException("Bootstrap output requires a POSIX filesystem for owner-only permissions");
        }
        Files.createFile(
                outputFile,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        String material = "loginId=" + loginId + System.lineSeparator()
                + "activationCode=" + activationCode + System.lineSeparator()
                + "expiresAt=" + expiresAt + System.lineSeparator();
        Files.writeString(
                outputFile,
                material,
                StandardCharsets.UTF_8,
                StandardOpenOption.WRITE);
    }
}
