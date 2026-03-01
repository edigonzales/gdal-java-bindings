package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.OgrDataSource;
import ch.so.agi.gdal.ffm.OgrDriverInfo;
import ch.so.agi.gdal.ffm.OgrFeature;
import ch.so.agi.gdal.ffm.OgrFieldDefinition;
import ch.so.agi.gdal.ffm.OgrFieldType;
import ch.so.agi.gdal.ffm.OgrGeometry;
import ch.so.agi.gdal.ffm.OgrLayerDefinition;
import ch.so.agi.gdal.ffm.OgrLayerReader;
import ch.so.agi.gdal.ffm.OgrLayerWriteSpec;
import ch.so.agi.gdal.ffm.OgrLayerWriter;
import ch.so.agi.gdal.ffm.OgrOpenOptions;
import ch.so.agi.gdal.ffm.OgrWriteMode;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OgrRuntime {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Object INIT_LOCK = new Object();

    private static final int GDAL_OF_UPDATE = 0x01;
    private static final int GDAL_OF_VECTOR = 0x04;
    private static final int GDAL_OF_VERBOSE_ERROR = 0x40;

    private static final int OGRERR_NONE = 0;
    private static final int WKB_BYTE_ORDER_NDR = 1;
    private static final int EWKB_SRID_FLAG = 0x2000_0000;
    private static final int WKB_HEADER_SIZE = 5;
    private static final int EWKB_SRID_SIZE = 4;

    private static final String DRIVER_CAPABILITY_CREATE_DATA_SOURCE = "CreateDataSource";
    private static final String DRIVER_CAPABILITY_DELETE_DATA_SOURCE = "DeleteDataSource";

    private static final String MD_DCAP_VECTOR = "DCAP_VECTOR";
    private static final String MD_DCAP_CREATE = "DCAP_CREATE";
    private static final String MD_DMD_EXTENSIONS = "DMD_EXTENSIONS";

    private OgrRuntime() {
    }

    public static OgrDataSource open(Path path, Map<String, String> openOptions) {
        return open(path, openOptions, false);
    }

    public static OgrDataSource create(
            Path path,
            String driverShortName,
            OgrWriteMode writeMode,
            Map<String, String> datasetCreationOptions
    ) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(driverShortName, "driverShortName must not be null");
        Objects.requireNonNull(writeMode, "writeMode must not be null");
        Objects.requireNonNull(datasetCreationOptions, "datasetCreationOptions must not be null");
        ensureInitialized();

        String normalizedDriverShortName = driverShortName.trim();
        if (normalizedDriverShortName.isEmpty()) {
            throw new IllegalArgumentException("driverShortName must not be blank");
        }

        Path absolutePath = path.toAbsolutePath();
        boolean datasetExists = Files.exists(absolutePath);

        if (datasetExists) {
            switch (writeMode) {
                case FAIL_IF_EXISTS -> throw new IllegalArgumentException(
                        "Target dataset already exists: " + absolutePath
                );
                case OVERWRITE -> {
                    MemorySegment driver = resolveDriverByName(normalizedDriverShortName);
                    deleteDataSource(driver, absolutePath);
                }
                case APPEND -> {
                    return open(
                            absolutePath,
                            Map.of(OgrOpenOptions.ALLOWED_DRIVERS, normalizedDriverShortName),
                            true
                    );
                }
                default -> throw new IllegalStateException("Unhandled write mode: " + writeMode);
            }
        }

        MemorySegment driver = resolveDriverByName(normalizedDriverShortName);
        MemorySegment dataset = createDataSource(driver, absolutePath, datasetCreationOptions);
        return new NativeOgrDataSource(absolutePath, dataset, true);
    }

    public static List<OgrDriverInfo> listWritableVectorDrivers() {
        ensureInitialized();

        int driverCount = GdalGenerated.OGRGetDriverCount();
        if (driverCount <= 0) {
            return List.of();
        }

        List<OgrDriverInfo> writableDrivers = new ArrayList<>();
        for (int i = 0; i < driverCount; i++) {
            MemorySegment ogrDriver = GdalGenerated.OGRGetDriver(i);
            if (CStrings.isNull(ogrDriver)) {
                continue;
            }

            String shortName = CStrings.fromCString(GdalGenerated.OGR_Dr_GetName(ogrDriver)).trim();
            if (shortName.isEmpty()) {
                continue;
            }

            boolean canCreate = testDriverCapability(ogrDriver, DRIVER_CAPABILITY_CREATE_DATA_SOURCE);
            if (!canCreate) {
                continue;
            }

            String longName = shortName;
            List<String> extensions = List.of();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment shortNameCString = arena.allocateFrom(shortName);
                MemorySegment gdalDriver = GdalGenerated.GDALGetDriverByName(shortNameCString);
                if (!CStrings.isNull(gdalDriver)) {
                    if (!isMetadataTrue(gdalDriver, MD_DCAP_VECTOR, arena)) {
                        continue;
                    }
                    String candidateLongName = CStrings.fromCString(
                            GdalGenerated.GDALGetDriverLongName(gdalDriver)
                    ).trim();
                    if (!candidateLongName.isEmpty()) {
                        longName = candidateLongName;
                    }
                    extensions = parseExtensions(readMetadataItem(gdalDriver, MD_DMD_EXTENSIONS, arena));
                }
            }

            writableDrivers.add(new OgrDriverInfo(shortName, longName, extensions, true, true));
        }

        writableDrivers.sort((a, b) -> a.shortName().compareToIgnoreCase(b.shortName()));
        return List.copyOf(writableDrivers);
    }

    private static OgrDataSource open(Path path, Map<String, String> openOptions, boolean writable) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(openOptions, "openOptions must not be null");
        ensureInitialized();

        OgrOptions.OpenOptions parsedOpenOptions = OgrOptions.parseOpenOptions(openOptions);
        String[] allowedDrivers = parsedOpenOptions.allowedDrivers().toArray(String[]::new);
        String[] datasetOptions = parsedOpenOptions.datasetOptions().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);

        int openFlags = GDAL_OF_VECTOR | GDAL_OF_VERBOSE_ERROR;
        if (writable) {
            openFlags |= GDAL_OF_UPDATE;
        }

        GdalGenerated.CPLErrorReset();
        MemorySegment dataset;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sourcePath = arena.allocateFrom(path.toAbsolutePath().toString());
            MemorySegment allowedDriversArgv = allowedDrivers.length == 0
                    ? MemorySegment.NULL
                    : CArgv.toCStringArray(allowedDrivers, arena);
            MemorySegment openOptionsArgv = datasetOptions.length == 0
                    ? MemorySegment.NULL
                    : CArgv.toCStringArray(datasetOptions, arena);

            dataset = GdalGenerated.GDALOpenEx(
                    sourcePath,
                    openFlags,
                    allowedDriversArgv,
                    openOptionsArgv,
                    MemorySegment.NULL
            );
        }
        if (CStrings.isNull(dataset)) {
            throw GdalErrors.lastError("Failed to open OGR datasource: " + path);
        }
        return new NativeOgrDataSource(path.toAbsolutePath(), dataset, writable);
    }

    private static MemorySegment resolveDriverByName(String driverShortName) {
        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment driverName = arena.allocateFrom(driverShortName);
            MemorySegment driver = GdalGenerated.OGRGetDriverByName(driverName);
            if (!CStrings.isNull(driver)) {
                return driver;
            }
        }

        List<String> available = listWritableVectorDrivers().stream().map(OgrDriverInfo::shortName).toList();
        throw new IllegalArgumentException(
                "OGR driver not found or not writable: '" + driverShortName + "'. Available drivers: " + available
        );
    }

    private static MemorySegment createDataSource(
            MemorySegment driver,
            Path path,
            Map<String, String> datasetCreationOptions
    ) {
        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathCString = arena.allocateFrom(path.toString());
            String[] options = toKeyValueArray(datasetCreationOptions);
            MemorySegment optionsArgv = options.length == 0 ? MemorySegment.NULL : CArgv.toCStringArray(options, arena);

            MemorySegment dataset = GdalGenerated.OGR_Dr_CreateDataSource(driver, pathCString, optionsArgv);
            if (CStrings.isNull(dataset)) {
                throw GdalErrors.lastError("Failed to create OGR datasource: " + path);
            }
            return dataset;
        }
    }

    private static void deleteDataSource(MemorySegment driver, Path path) {
        GdalGenerated.CPLErrorReset();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathCString = arena.allocateFrom(path.toString());
            if (!testDriverCapability(driver, DRIVER_CAPABILITY_DELETE_DATA_SOURCE)) {
                throw new IllegalArgumentException(
                        "Driver does not support overwrite/delete for existing dataset: " + path
                );
            }
            int errorCode = GdalGenerated.OGR_Dr_DeleteDataSource(driver, pathCString);
            throwIfOgrError(errorCode, "Failed to overwrite existing OGR datasource: " + path);
        }
    }

    private static boolean testDriverCapability(MemorySegment driver, String capability) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capabilityCString = arena.allocateFrom(capability);
            return GdalGenerated.OGR_Dr_TestCapability(driver, capabilityCString) != 0;
        }
    }

    private static String readMetadataItem(MemorySegment driver, String key, Arena arena) {
        MemorySegment keyCString = arena.allocateFrom(key);
        return CStrings.fromCString(GdalGenerated.GDALGetMetadataItem(driver, keyCString, MemorySegment.NULL)).trim();
    }

    private static boolean isMetadataTrue(MemorySegment driver, String key, Arena arena) {
        String value = readMetadataItem(driver, key, arena);
        return "YES".equalsIgnoreCase(value) || "TRUE".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static List<String> parseExtensions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String[] split = raw.split("[,;\\s]+");
        LinkedHashSet<String> extensions = new LinkedHashSet<>();
        for (String extension : split) {
            if (extension == null) {
                continue;
            }
            String trimmed = extension.trim();
            if (!trimmed.isEmpty()) {
                extensions.add(trimmed);
            }
        }
        return List.copyOf(extensions);
    }

    private static String[] toKeyValueArray(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return new String[0];
        }
        List<String> keyValues = new ArrayList<>(options.size());
        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            keyValues.add(key.trim() + "=" + value);
        }
        return keyValues.toArray(String[]::new);
    }

    private static final class NativeOgrDataSource implements OgrDataSource {
        private final Path sourcePath;
        private final MemorySegment dataset;
        private final boolean writable;
        private volatile boolean closed;

        private NativeOgrDataSource(Path sourcePath, MemorySegment dataset, boolean writable) {
            this.sourcePath = sourcePath;
            this.dataset = dataset;
            this.writable = writable;
        }

        @Override
        public synchronized List<OgrLayerDefinition> listLayers() {
            ensureOpen();

            int layerCount = GdalGenerated.GDALDatasetGetLayerCount(dataset);
            if (layerCount <= 0) {
                return List.of();
            }

            List<OgrLayerDefinition> definitions = new ArrayList<>(layerCount);
            for (int i = 0; i < layerCount; i++) {
                MemorySegment layer = GdalGenerated.GDALDatasetGetLayer(dataset, i);
                if (CStrings.isNull(layer)) {
                    continue;
                }
                definitions.add(describeLayer(layer));
            }
            return List.copyOf(definitions);
        }

        @Override
        public synchronized OgrLayerReader openReader(String layerName, Map<String, String> options) {
            ensureOpen();

            Map<String, String> safeOptions = options == null ? Map.of() : options;
            OgrOptions.ReaderOptions parsedOptions = OgrOptions.parseReaderOptions(safeOptions);

            MemorySegment layer = resolveLayer(layerName);
            if (CStrings.isNull(layer)) {
                throw new IllegalArgumentException(
                        "Layer '" + layerName + "' was not found in datasource '" + sourcePath + "'."
                );
            }

            OgrLayerDefinition layerDefinition = describeLayer(layer);
            int[] projectedFieldIndices = resolveProjectedFieldIndices(layerDefinition, parsedOptions);

            configureLayer(layer, layerDefinition, parsedOptions);
            GdalGenerated.OGR_L_ResetReading(layer);

            Long limit = parsedOptions.limit();
            long rowLimit = limit == null ? Long.MAX_VALUE : limit;
            return new NativeOgrLayerReader(this, layer, layerDefinition, projectedFieldIndices, rowLimit);
        }

        @Override
        public synchronized OgrLayerWriter openWriter(OgrLayerWriteSpec spec) {
            ensureOpen();
            if (!writable) {
                throw new IllegalStateException(
                        "Datasource was opened read-only. Use Ogr.create(...) or open in write mode for writing."
                );
            }
            Objects.requireNonNull(spec, "spec must not be null");

            Map<String, String> effectiveLayerCreationOptions = withOptionalIdFieldNames(
                    spec.layerCreationOptions(),
                    spec.fidFieldName(),
                    spec.geometryFieldName()
            );

            MemorySegment layer = resolveLayerOrNull(spec.layerName());
            switch (spec.writeMode()) {
                case FAIL_IF_EXISTS -> {
                    if (!CStrings.isNull(layer)) {
                        throw new IllegalArgumentException(
                                "Layer already exists in target datasource: " + spec.layerName()
                        );
                    }
                }
                case OVERWRITE -> {
                    if (!CStrings.isNull(layer)) {
                        deleteLayer(spec.layerName());
                        layer = MemorySegment.NULL;
                    }
                }
                case APPEND -> {
                    // Keep existing layer if present.
                }
                default -> throw new IllegalStateException("Unhandled write mode: " + spec.writeMode());
            }

            if (CStrings.isNull(layer)) {
                layer = createLayer(spec.layerName(), spec.geometryTypeCode(), effectiveLayerCreationOptions, spec.fields());
            } else {
                validateExistingLayerSchema(layer, spec.fields());
            }

            OgrLayerDefinition layerDefinition = describeLayer(layer);
            int geometryFieldIndex = resolveGeometryFieldIndex(layer, spec.geometryFieldName());
            return new NativeOgrLayerWriter(this, layer, layerDefinition, geometryFieldIndex);
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeDatasetQuietly(dataset);
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Datasource is closed: " + sourcePath);
            }
        }

        private MemorySegment resolveLayer(String layerName) {
            if (layerName == null || layerName.isBlank()) {
                MemorySegment firstLayer = GdalGenerated.GDALDatasetGetLayer(dataset, 0);
                if (CStrings.isNull(firstLayer)) {
                    throw new IllegalArgumentException("Datasource contains no readable OGR layers: " + sourcePath);
                }
                return firstLayer;
            }

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment layerNameCString = arena.allocateFrom(layerName);
                return GdalGenerated.GDALDatasetGetLayerByName(dataset, layerNameCString);
            }
        }

        private MemorySegment resolveLayerOrNull(String layerName) {
            if (layerName == null || layerName.isBlank()) {
                return MemorySegment.NULL;
            }
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment layerNameCString = arena.allocateFrom(layerName);
                return GdalGenerated.GDALDatasetGetLayerByName(dataset, layerNameCString);
            }
        }

        private void deleteLayer(String layerName) {
            int index = findLayerIndex(layerName);
            if (index < 0) {
                return;
            }
            GdalGenerated.CPLErrorReset();
            int errorCode = GdalGenerated.OGR_DS_DeleteLayer(dataset, index);
            throwIfOgrError(errorCode, "Failed to delete existing layer '" + layerName + "'");
        }

        private int findLayerIndex(String layerName) {
            int layerCount = GdalGenerated.GDALDatasetGetLayerCount(dataset);
            for (int i = 0; i < layerCount; i++) {
                MemorySegment layer = GdalGenerated.GDALDatasetGetLayer(dataset, i);
                if (CStrings.isNull(layer)) {
                    continue;
                }
                String currentName = CStrings.fromCString(GdalGenerated.OGR_L_GetName(layer));
                if (layerName.equals(currentName)) {
                    return i;
                }
            }
            return -1;
        }

        private MemorySegment createLayer(
                String layerName,
                Integer geometryTypeCode,
                Map<String, String> layerCreationOptions,
                List<OgrFieldDefinition> fields
        ) {
            int effectiveGeometryTypeCode = geometryTypeCode == null ? 0 : geometryTypeCode;
            if (geometryTypeCode == null) {
                throw new IllegalArgumentException(
                        "geometryTypeCode is required when creating a new layer: " + layerName
                );
            }

            GdalGenerated.CPLErrorReset();
            MemorySegment layer;
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment layerNameCString = arena.allocateFrom(layerName);
                String[] options = toKeyValueArray(layerCreationOptions);
                MemorySegment layerOptionsArgv = options.length == 0
                        ? MemorySegment.NULL
                        : CArgv.toCStringArray(options, arena);

                layer = GdalGenerated.OGR_DS_CreateLayer(
                        dataset,
                        layerNameCString,
                        MemorySegment.NULL,
                        effectiveGeometryTypeCode,
                        layerOptionsArgv
                );
            }
            if (CStrings.isNull(layer)) {
                throw GdalErrors.lastError("Failed to create OGR layer: " + layerName);
            }

            addFields(layer, fields);
            return layer;
        }

        private void addFields(MemorySegment layer, List<OgrFieldDefinition> fields) {
            if (fields == null || fields.isEmpty()) {
                return;
            }

            for (OgrFieldDefinition field : fields) {
                String fieldName = field.name() == null ? "" : field.name().trim();
                if (fieldName.isEmpty()) {
                    throw new IllegalArgumentException("Field name must not be blank");
                }

                OgrFieldType fieldType = field.type();
                if (fieldType == OgrFieldType.UNKNOWN) {
                    throw new IllegalArgumentException(
                            "Field type UNKNOWN is not writable for field '" + fieldName + "'"
                    );
                }

                GdalGenerated.CPLErrorReset();
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment fieldNameCString = arena.allocateFrom(fieldName);
                    MemorySegment fieldDefn = GdalGenerated.OGR_Fld_Create(fieldNameCString, fieldType.nativeCode());
                    if (CStrings.isNull(fieldDefn)) {
                        throw GdalErrors.lastError("Failed to allocate field definition for: " + fieldName);
                    }
                    try {
                        int errorCode = GdalGenerated.OGR_L_CreateField(layer, fieldDefn, 1);
                        throwIfOgrError(errorCode, "Failed to create field: " + fieldName);
                    } finally {
                        GdalGenerated.OGR_Fld_Destroy(fieldDefn);
                    }
                }
            }
        }

        private void validateExistingLayerSchema(MemorySegment layer, List<OgrFieldDefinition> requestedFields) {
            if (requestedFields == null || requestedFields.isEmpty()) {
                return;
            }

            OgrLayerDefinition existing = describeLayer(layer);
            Map<String, OgrFieldType> byLowercaseName = new LinkedHashMap<>();
            for (OgrFieldDefinition existingField : existing.fields()) {
                byLowercaseName.put(existingField.name().toLowerCase(Locale.ROOT), existingField.type());
            }

            for (OgrFieldDefinition requestedField : requestedFields) {
                String lower = requestedField.name().toLowerCase(Locale.ROOT);
                OgrFieldType existingType = byLowercaseName.get(lower);
                if (existingType == null) {
                    throw new IllegalArgumentException(
                            "Append target layer is missing field '" + requestedField.name() + "'"
                    );
                }
                if (requestedField.type() != OgrFieldType.UNKNOWN && existingType != requestedField.type()) {
                    throw new IllegalArgumentException(
                            "Append field type mismatch for '" + requestedField.name() + "': expected "
                                    + requestedField.type() + " but layer has " + existingType
                    );
                }
            }
        }

        private int resolveGeometryFieldIndex(MemorySegment layer, String geometryFieldName) {
            if (geometryFieldName == null || geometryFieldName.isBlank()) {
                return -1;
            }

            MemorySegment layerDefinition = GdalGenerated.OGR_L_GetLayerDefn(layer);
            if (CStrings.isNull(layerDefinition)) {
                return -1;
            }

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment geometryFieldNameCString = arena.allocateFrom(geometryFieldName);
                int geometryFieldIndex = GdalGenerated.OGR_FD_GetGeomFieldIndex(layerDefinition, geometryFieldNameCString);
                if (geometryFieldIndex < 0) {
                    throw new IllegalArgumentException(
                            "Geometry field '" + geometryFieldName + "' does not exist in target layer"
                    );
                }
                return geometryFieldIndex;
            }
        }

        private static Map<String, String> withOptionalIdFieldNames(
                Map<String, String> layerCreationOptions,
                String fidFieldName,
                String geometryFieldName
        ) {
            Map<String, String> normalized = new LinkedHashMap<>();
            if (layerCreationOptions != null) {
                normalized.putAll(layerCreationOptions);
            }
            if (fidFieldName != null && !fidFieldName.isBlank()) {
                normalized.putIfAbsent("FID", fidFieldName.trim());
            }
            if (geometryFieldName != null && !geometryFieldName.isBlank()) {
                normalized.putIfAbsent("GEOMETRY_NAME", geometryFieldName.trim());
            }
            return Map.copyOf(normalized);
        }

        private static void configureLayer(
                MemorySegment layer,
                OgrLayerDefinition layerDefinition,
                OgrOptions.ReaderOptions options
        ) {
            applyIgnoredFields(layer, layerDefinition, options.selectedFieldsLowercase());
            applyAttributeFilter(layer, options.attributeFilter());
            applySpatialFilter(layer, options);
        }

        private static void applyIgnoredFields(
                MemorySegment layer,
                OgrLayerDefinition layerDefinition,
                Set<String> selectedFieldsLowercase
        ) {
            if (selectedFieldsLowercase.isEmpty()) {
                applyIgnoredFields(layer, List.of());
                return;
            }

            List<String> ignoredFields = new ArrayList<>();
            for (OgrFieldDefinition field : layerDefinition.fields()) {
                if (!selectedFieldsLowercase.contains(field.name().toLowerCase(Locale.ROOT))) {
                    ignoredFields.add(field.name());
                }
            }
            applyIgnoredFields(layer, ignoredFields);
        }

        private static void applyIgnoredFields(MemorySegment layer, List<String> ignoredFields) {
            GdalGenerated.CPLErrorReset();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment ignoredFieldsArgv = ignoredFields.isEmpty()
                        ? MemorySegment.NULL
                        : CArgv.toCStringArray(ignoredFields.toArray(String[]::new), arena);

                int ogrError = GdalGenerated.OGR_L_SetIgnoredFields(layer, ignoredFieldsArgv);
                throwIfOgrError(ogrError, "Failed to configure ignored fields");
            }
        }

        private static void applyAttributeFilter(MemorySegment layer, String attributeFilter) {
            GdalGenerated.CPLErrorReset();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment attributeFilterCString = attributeFilter == null
                        ? MemorySegment.NULL
                        : arena.allocateFrom(attributeFilter);

                int ogrError = GdalGenerated.OGR_L_SetAttributeFilter(layer, attributeFilterCString);
                throwIfOgrError(ogrError, "Failed to configure attribute filter");
            }
        }

        private static void applySpatialFilter(MemorySegment layer, OgrOptions.ReaderOptions options) {
            if (options.bbox() != null) {
                OgrOptions.BoundingBox bbox = options.bbox();
                GdalGenerated.OGR_L_SetSpatialFilterRect(layer, bbox.minX(), bbox.minY(), bbox.maxX(), bbox.maxY());
                return;
            }

            if (options.spatialFilterWkt() == null) {
                GdalGenerated.OGR_L_SetSpatialFilter(layer, MemorySegment.NULL);
                return;
            }

            GdalGenerated.CPLErrorReset();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment wktCString = arena.allocateFrom(options.spatialFilterWkt());
                MemorySegment wktPointerPointer = arena.allocate(ValueLayout.ADDRESS);
                wktPointerPointer.set(ValueLayout.ADDRESS, 0, wktCString);

                MemorySegment geometryOut = arena.allocate(ValueLayout.ADDRESS);
                int createGeometryErr = GdalGenerated.OGR_G_CreateFromWkt(
                        wktPointerPointer,
                        MemorySegment.NULL,
                        geometryOut
                );
                throwIfOgrError(createGeometryErr, "Failed to parse spatial filter WKT");

                MemorySegment filterGeometry = geometryOut.get(ValueLayout.ADDRESS, 0);
                if (CStrings.isNull(filterGeometry)) {
                    throw GdalErrors.lastError("Failed to parse spatial filter WKT");
                }

                try {
                    GdalGenerated.OGR_L_SetSpatialFilter(layer, filterGeometry);
                } finally {
                    GdalGenerated.OGR_G_DestroyGeometry(filterGeometry);
                }
            }
        }
    }

    private static final class NativeOgrLayerReader implements OgrLayerReader {
        private final NativeOgrDataSource dataSource;
        private final MemorySegment layer;
        private final OgrLayerDefinition layerDefinition;
        private final int[] projectedFieldIndices;
        private final long rowLimit;

        private boolean closed;
        private boolean iteratorCreated;
        private boolean fetched;
        private OgrFeature buffered;
        private long emitted;

        private NativeOgrLayerReader(
                NativeOgrDataSource dataSource,
                MemorySegment layer,
                OgrLayerDefinition layerDefinition,
                int[] projectedFieldIndices,
                long rowLimit
        ) {
            this.dataSource = dataSource;
            this.layer = layer;
            this.layerDefinition = layerDefinition;
            this.projectedFieldIndices = projectedFieldIndices;
            this.rowLimit = rowLimit;
        }

        @Override
        public synchronized Iterator<OgrFeature> iterator() {
            ensureOpen();
            if (iteratorCreated) {
                throw new IllegalStateException("Only a single iterator is supported per OgrLayerReader");
            }
            iteratorCreated = true;

            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return fetchNextIfNeeded() != null;
                }

                @Override
                public OgrFeature next() {
                    OgrFeature next = fetchNextIfNeeded();
                    if (next == null) {
                        throw new NoSuchElementException();
                    }
                    fetched = false;
                    buffered = null;
                    return next;
                }
            };
        }

        @Override
        public synchronized void close() {
            closed = true;
            fetched = true;
            buffered = null;
        }

        private OgrFeature fetchNextIfNeeded() {
            if (fetched) {
                return buffered;
            }
            ensureOpen();
            fetched = true;

            if (emitted >= rowLimit) {
                buffered = null;
                return null;
            }

            MemorySegment nativeFeature = GdalGenerated.OGR_L_GetNextFeature(layer);
            if (CStrings.isNull(nativeFeature)) {
                buffered = null;
                return null;
            }

            try {
                buffered = toFeature(nativeFeature, layerDefinition, projectedFieldIndices);
                emitted++;
                return buffered;
            } finally {
                GdalGenerated.OGR_F_Destroy(nativeFeature);
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Layer reader is closed");
            }
            dataSource.ensureOpen();
        }
    }

    private static final class NativeOgrLayerWriter implements OgrLayerWriter {
        private final NativeOgrDataSource dataSource;
        private final MemorySegment layer;
        private final OgrLayerDefinition layerDefinition;
        private final int geometryFieldIndex;

        private boolean closed;

        private NativeOgrLayerWriter(
                NativeOgrDataSource dataSource,
                MemorySegment layer,
                OgrLayerDefinition layerDefinition,
                int geometryFieldIndex
        ) {
            this.dataSource = dataSource;
            this.layer = layer;
            this.layerDefinition = layerDefinition;
            this.geometryFieldIndex = geometryFieldIndex;
        }

        @Override
        public synchronized void write(OgrFeature feature) {
            ensureOpen();
            Objects.requireNonNull(feature, "feature must not be null");

            GdalGenerated.CPLErrorReset();

            MemorySegment layerDefinitionHandle = GdalGenerated.OGR_L_GetLayerDefn(layer);
            if (CStrings.isNull(layerDefinitionHandle)) {
                throw GdalErrors.lastError("Failed to resolve layer definition for writing");
            }

            MemorySegment nativeFeature = GdalGenerated.OGR_F_Create(layerDefinitionHandle);
            if (CStrings.isNull(nativeFeature)) {
                throw GdalErrors.lastError("Failed to create native OGR feature");
            }

            try (Arena arena = Arena.ofConfined()) {
                if (feature.fid() >= 0) {
                    int setFidError = GdalGenerated.OGR_F_SetFID(nativeFeature, feature.fid());
                    throwIfOgrError(setFidError, "Failed to set feature FID");
                }

                writeAttributes(nativeFeature, feature.attributes(), arena);
                writeGeometry(nativeFeature, feature.geometry(), arena);

                int createFeatureError = GdalGenerated.OGR_L_CreateFeature(layer, nativeFeature);
                throwIfOgrError(createFeatureError, "Failed to write feature");
            } finally {
                GdalGenerated.OGR_F_Destroy(nativeFeature);
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Layer writer is closed");
            }
            dataSource.ensureOpen();
        }

        private void writeAttributes(
                MemorySegment nativeFeature,
                Map<String, Object> attributes,
                Arena arena
        ) {
            if (attributes == null || attributes.isEmpty()) {
                return;
            }

            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String fieldName = entry.getKey();
                if (fieldName == null || fieldName.isBlank()) {
                    continue;
                }

                MemorySegment fieldNameCString = arena.allocateFrom(fieldName);
                int fieldIndex = GdalGenerated.OGR_F_GetFieldIndex(nativeFeature, fieldNameCString);
                if (fieldIndex < 0) {
                    throw new IllegalArgumentException(
                            "Field '" + fieldName + "' does not exist in target layer '" + layerDefinition.name() + "'"
                    );
                }

                Object value = entry.getValue();
                if (value == null) {
                    GdalGenerated.OGR_F_SetFieldNull(nativeFeature, fieldIndex);
                    continue;
                }

                if (value instanceof Boolean boolValue) {
                    GdalGenerated.OGR_F_SetFieldInteger64(nativeFeature, fieldIndex, boolValue ? 1L : 0L);
                    continue;
                }

                if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                    GdalGenerated.OGR_F_SetFieldInteger64(nativeFeature, fieldIndex, ((Number) value).longValue());
                    continue;
                }

                if (value instanceof BigInteger bigInteger) {
                    GdalGenerated.OGR_F_SetFieldInteger64(nativeFeature, fieldIndex, bigInteger.longValue());
                    continue;
                }

                if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
                    GdalGenerated.OGR_F_SetFieldDouble(nativeFeature, fieldIndex, ((Number) value).doubleValue());
                    continue;
                }

                MemorySegment stringValue = arena.allocateFrom(value.toString());
                GdalGenerated.OGR_F_SetFieldString(nativeFeature, fieldIndex, stringValue);
            }
        }

        private void writeGeometry(
                MemorySegment nativeFeature,
                OgrGeometry geometry,
                Arena arena
        ) {
            if (geometry == null) {
                return;
            }

            byte[] wkb = normalizeToWkb(geometry.ewkb());
            MemorySegment ewkbNative = arena.allocate(wkb.length);
            MemorySegment.copy(MemorySegment.ofArray(wkb), 0, ewkbNative, 0, wkb.length);
            MemorySegment geometryOut = arena.allocate(ValueLayout.ADDRESS);
            int createGeometryErr = GdalGenerated.OGR_G_CreateFromWkb(
                    ewkbNative,
                    MemorySegment.NULL,
                    geometryOut,
                    wkb.length
            );
            throwIfOgrError(createGeometryErr, "Failed to decode feature geometry from EWKB/WKB");

            MemorySegment nativeGeometry = geometryOut.get(ValueLayout.ADDRESS, 0);
            if (CStrings.isNull(nativeGeometry)) {
                throw GdalErrors.lastError("Failed to decode feature geometry from EWKB/WKB");
            }

            try {
                int setGeometryError;
                if (geometryFieldIndex >= 0) {
                    setGeometryError = GdalGenerated.OGR_F_SetGeomField(nativeFeature, geometryFieldIndex, nativeGeometry);
                } else {
                    setGeometryError = GdalGenerated.OGR_F_SetGeometry(nativeFeature, nativeGeometry);
                }
                throwIfOgrError(setGeometryError, "Failed to set feature geometry");
            } finally {
                GdalGenerated.OGR_G_DestroyGeometry(nativeGeometry);
            }
        }

        private static byte[] normalizeToWkb(byte[] ewkbOrWkb) {
            if (ewkbOrWkb.length < WKB_HEADER_SIZE) {
                return ewkbOrWkb;
            }

            ByteOrder order = switch (ewkbOrWkb[0]) {
                case 0 -> ByteOrder.BIG_ENDIAN;
                case 1 -> ByteOrder.LITTLE_ENDIAN;
                default -> throw new IllegalArgumentException(
                        "Unsupported WKB byte order marker: " + ewkbOrWkb[0]
                );
            };

            ByteBuffer in = ByteBuffer.wrap(ewkbOrWkb).order(order);
            int rawType = in.getInt(1);
            if ((rawType & EWKB_SRID_FLAG) == 0) {
                return ewkbOrWkb;
            }

            if (ewkbOrWkb.length < WKB_HEADER_SIZE + EWKB_SRID_SIZE) {
                throw new IllegalArgumentException("Invalid EWKB payload with SRID flag: payload too short");
            }

            byte[] normalized = new byte[ewkbOrWkb.length - EWKB_SRID_SIZE];
            normalized[0] = ewkbOrWkb[0];
            ByteBuffer out = ByteBuffer.wrap(normalized).order(order);
            out.putInt(1, rawType & ~EWKB_SRID_FLAG);

            System.arraycopy(
                    ewkbOrWkb,
                    WKB_HEADER_SIZE + EWKB_SRID_SIZE,
                    normalized,
                    WKB_HEADER_SIZE,
                    ewkbOrWkb.length - WKB_HEADER_SIZE - EWKB_SRID_SIZE
            );
            return normalized;
        }
    }

    private static OgrLayerDefinition describeLayer(MemorySegment layer) {
        String layerName = CStrings.fromCString(GdalGenerated.OGR_L_GetName(layer));
        int geometryType = GdalGenerated.OGR_L_GetGeomType(layer);

        MemorySegment layerDefinition = GdalGenerated.OGR_L_GetLayerDefn(layer);
        if (CStrings.isNull(layerDefinition)) {
            return new OgrLayerDefinition(layerName, geometryType, List.of());
        }

        int fieldCount = GdalGenerated.OGR_FD_GetFieldCount(layerDefinition);
        List<OgrFieldDefinition> fields = new ArrayList<>(Math.max(fieldCount, 0));
        for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
            MemorySegment fieldDefn = GdalGenerated.OGR_FD_GetFieldDefn(layerDefinition, fieldIndex);
            if (CStrings.isNull(fieldDefn)) {
                continue;
            }

            String fieldName = CStrings.fromCString(GdalGenerated.OGR_Fld_GetNameRef(fieldDefn));
            int nativeFieldType = GdalGenerated.OGR_Fld_GetType(fieldDefn);
            fields.add(new OgrFieldDefinition(fieldName, OgrFieldType.fromNativeCode(nativeFieldType)));
        }
        return new OgrLayerDefinition(layerName, geometryType, List.copyOf(fields));
    }

    private static int[] resolveProjectedFieldIndices(
            OgrLayerDefinition layerDefinition,
            OgrOptions.ReaderOptions options
    ) {
        if (options.selectedFields().isEmpty()) {
            int[] allFieldIndexes = new int[layerDefinition.fields().size()];
            for (int i = 0; i < allFieldIndexes.length; i++) {
                allFieldIndexes[i] = i;
            }
            return allFieldIndexes;
        }

        Set<String> selectedLowercase = options.selectedFieldsLowercase();
        Set<String> knownFieldsLowercase = new java.util.HashSet<>();
        for (OgrFieldDefinition field : layerDefinition.fields()) {
            knownFieldsLowercase.add(field.name().toLowerCase(Locale.ROOT));
        }
        List<String> unknownSelectedFields = new ArrayList<>();
        for (String selectedField : options.selectedFields()) {
            if (!knownFieldsLowercase.contains(selectedField.toLowerCase(Locale.ROOT))) {
                unknownSelectedFields.add(selectedField);
            }
        }
        if (!unknownSelectedFields.isEmpty()) {
            throw new IllegalArgumentException("Selected fields were not found in layer: " + unknownSelectedFields);
        }

        List<Integer> projected = new ArrayList<>();
        List<OgrFieldDefinition> fields = layerDefinition.fields();
        for (int index = 0; index < fields.size(); index++) {
            String lower = fields.get(index).name().toLowerCase(Locale.ROOT);
            if (selectedLowercase.contains(lower)) {
                projected.add(index);
            }
        }

        int[] projectedArray = new int[projected.size()];
        for (int i = 0; i < projected.size(); i++) {
            projectedArray[i] = projected.get(i);
        }
        return projectedArray;
    }

    private static OgrFeature toFeature(
            MemorySegment feature,
            OgrLayerDefinition layerDefinition,
            int[] projectedFieldIndices
    ) {
        long fid = GdalGenerated.OGR_F_GetFID(feature);
        Map<String, Object> attributes = extractAttributes(feature, layerDefinition, projectedFieldIndices);
        OgrGeometry geometry = extractGeometry(feature);
        return new OgrFeature(fid, attributes, geometry);
    }

    private static Map<String, Object> extractAttributes(
            MemorySegment feature,
            OgrLayerDefinition layerDefinition,
            int[] projectedFieldIndices
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>(projectedFieldIndices.length);
        for (int projectedFieldIndex : projectedFieldIndices) {
            OgrFieldDefinition fieldDefinition = layerDefinition.fields().get(projectedFieldIndex);
            Object value = readFieldValue(feature, projectedFieldIndex, fieldDefinition.type());
            attributes.put(fieldDefinition.name(), value);
        }
        return attributes;
    }

    private static Object readFieldValue(MemorySegment feature, int fieldIndex, OgrFieldType fieldType) {
        if (GdalGenerated.OGR_F_IsFieldSetAndNotNull(feature, fieldIndex) == 0) {
            return null;
        }

        return switch (fieldType) {
            case INTEGER, INTEGER64 -> GdalGenerated.OGR_F_GetFieldAsInteger64(feature, fieldIndex);
            case REAL -> GdalGenerated.OGR_F_GetFieldAsDouble(feature, fieldIndex);
            default -> CStrings.fromCString(GdalGenerated.OGR_F_GetFieldAsString(feature, fieldIndex));
        };
    }

    private static OgrGeometry extractGeometry(MemorySegment feature) {
        MemorySegment geometry = GdalGenerated.OGR_F_GetGeometryRef(feature);
        if (CStrings.isNull(geometry)) {
            return null;
        }

        int wkbSize = GdalGenerated.OGR_G_WkbSize(geometry);
        if (wkbSize <= 0) {
            return null;
        }

        byte[] wkb = new byte[wkbSize];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeWkb = arena.allocate(wkbSize);
            int exportErr = GdalGenerated.OGR_G_ExportToWkb(geometry, WKB_BYTE_ORDER_NDR, nativeWkb);
            throwIfOgrError(exportErr, "Failed to export geometry as WKB");
            MemorySegment.copy(nativeWkb, 0, MemorySegment.ofArray(wkb), 0, wkbSize);
        }

        OptionalInt srid = readSrid(geometry);
        if (srid.isPresent()) {
            return OgrGeometry.fromWkb(wkb, srid.getAsInt());
        }
        return OgrGeometry.fromWkb(wkb);
    }

    private static OptionalInt readSrid(MemorySegment geometry) {
        MemorySegment spatialReference = GdalGenerated.OGR_G_GetSpatialReference(geometry);
        if (CStrings.isNull(spatialReference)) {
            return OptionalInt.empty();
        }

        String authorityCode = readAuthorityCode(spatialReference, "PROJCS");
        if (authorityCode == null) {
            authorityCode = readAuthorityCode(spatialReference, "GEOGCS");
        }
        if (authorityCode == null) {
            authorityCode = readAuthorityCode(spatialReference, null);
        }
        if (authorityCode == null) {
            return OptionalInt.empty();
        }

        try {
            return OptionalInt.of(Integer.parseInt(authorityCode));
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    private static String readAuthorityCode(MemorySegment spatialReference, String targetKey) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment targetKeyCString = targetKey == null ? MemorySegment.NULL : arena.allocateFrom(targetKey);
            String authorityCode = CStrings.fromCString(
                    GdalGenerated.OSRGetAuthorityCode(spatialReference, targetKeyCString)
            ).trim();
            return authorityCode.isEmpty() ? null : authorityCode;
        }
    }

    private static void throwIfOgrError(int errorCode, String message) {
        if (errorCode == OGRERR_NONE) {
            return;
        }
        throw GdalErrors.lastError(message);
    }

    private static void ensureInitialized() {
        if (INITIALIZED.get()) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (INITIALIZED.get()) {
                return;
            }

            NativeAccess.ensureEnabled();
            NativeBundleInfo bundleInfo = NativeLoader.load();
            applyConfig(bundleInfo);
            GdalGenerated.GDALAllRegister();
            INITIALIZED.set(true);
        }
    }

    private static void applyConfig(NativeBundleInfo bundleInfo) {
        try (Arena arena = Arena.ofConfined()) {
            setConfigOption(arena, "GDAL_DATA", bundleInfo.gdalData());
            setConfigOption(arena, "PROJ_LIB", bundleInfo.projData());
            setConfigOption(arena, "GDAL_DRIVER_PATH", bundleInfo.driverPath());
        }
    }

    private static void setConfigOption(Arena arena, String key, Path value) {
        if (value == null) {
            return;
        }

        MemorySegment keyString = arena.allocateFrom(key);
        MemorySegment valueString = arena.allocateFrom(value.toAbsolutePath().toString());
        GdalGenerated.CPLSetConfigOption(keyString, valueString);
        System.setProperty(key, value.toAbsolutePath().toString());
    }

    private static void closeDatasetQuietly(MemorySegment dataset) {
        if (CStrings.isNull(dataset)) {
            return;
        }
        try {
            GdalGenerated.GDALClose(dataset);
        } catch (RuntimeException ignored) {
            // Keep cleanup best-effort and preserve root cause.
        }
    }
}
