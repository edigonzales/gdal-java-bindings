package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.OgrDataSource;
import ch.so.agi.gdal.ffm.OgrFeature;
import ch.so.agi.gdal.ffm.OgrFieldDefinition;
import ch.so.agi.gdal.ffm.OgrFieldType;
import ch.so.agi.gdal.ffm.OgrGeometry;
import ch.so.agi.gdal.ffm.OgrLayerDefinition;
import ch.so.agi.gdal.ffm.OgrLayerReader;
import ch.so.agi.gdal.ffm.OgrLayerWriter;
import ch.so.agi.gdal.ffm.generated.GdalGenerated;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

    private static final int GDAL_OF_VECTOR = 0x04;
    private static final int GDAL_OF_VERBOSE_ERROR = 0x40;

    private static final int OGRERR_NONE = 0;
    private static final int WKB_BYTE_ORDER_NDR = 1;

    private OgrRuntime() {
    }

    public static OgrDataSource open(Path path, Map<String, String> openOptions) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(openOptions, "openOptions must not be null");
        ensureInitialized();

        OgrOptions.OpenOptions parsedOpenOptions = OgrOptions.parseOpenOptions(openOptions);
        String[] allowedDrivers = parsedOpenOptions.allowedDrivers().toArray(String[]::new);
        String[] datasetOptions = parsedOpenOptions.datasetOptions().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);

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
                    GDAL_OF_VECTOR | GDAL_OF_VERBOSE_ERROR,
                    allowedDriversArgv,
                    openOptionsArgv,
                    MemorySegment.NULL
            );
        }
        if (CStrings.isNull(dataset)) {
            throw GdalErrors.lastError("Failed to open OGR datasource: " + path);
        }
        return new NativeOgrDataSource(path, dataset);
    }

    private static final class NativeOgrDataSource implements OgrDataSource {
        private final Path sourcePath;
        private final MemorySegment dataset;
        private volatile boolean closed;

        private NativeOgrDataSource(Path sourcePath, MemorySegment dataset) {
            this.sourcePath = sourcePath;
            this.dataset = dataset;
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
        public OgrLayerWriter openWriter(String layerName, Map<String, String> options) {
            throw new UnsupportedOperationException(
                    "OGR write API is not implemented yet for datasource '" + sourcePath + "'."
            );
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
