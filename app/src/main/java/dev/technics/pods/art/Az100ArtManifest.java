package dev.technics.pods.art;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** Versioned, reviewed model/color mapping; resource names are never guessed at runtime. */
public final class Az100ArtManifest {
    public static final int SCHEMA_VERSION = 1;
    public static final String MODEL = "EAH-AZ100";
    public static final String OFFICIAL_PACKAGE = "com.panasonic.technicsaudioconnect";
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;
    public record Slot(String name, String resource, String resourceSha256, String file,
                       String sha256, int width, int height) { }

    private final String source, color, sourceVersion, signatureSha256, deviceAddress;
    private final long sourceVersionCode;
    private final boolean verified;
    private final Map<String, Slot> slots;

    public Az100ArtManifest(String source, String color, String sourceVersion, long sourceVersionCode,
            String signatureSha256, String deviceAddress, boolean verified, Map<String, Slot> slots) {
        this.source = source;
        this.color = color;
        this.sourceVersion = sourceVersion;
        this.sourceVersionCode = sourceVersionCode;
        this.signatureSha256 = signatureSha256;
        this.deviceAddress = deviceAddress;
        this.verified = verified;
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
    }

    public static Az100ArtManifest empty() {
        return new Az100ArtManifest("unverified", "unspecified", "", 0, "", "", false, Map.of());
    }
    public String model() { return MODEL; }
    public String source() { return source; }
    public String color() { return color; }
    public String sourceVersion() { return sourceVersion; }
    public long sourceVersionCode() { return sourceVersionCode; }
    public String signatureSha256() { return signatureSha256; }
    public String deviceAddress() { return deviceAddress; }
    public boolean verified() { return verified; }
    public Map<String, Slot> slots() { return slots; }
    public Slot slot(String name) { return slots.get(name); }

    public byte[] encode() {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = document.createElement("az100-art"); document.appendChild(root);
            root.setAttribute("schema", "1"); root.setAttribute("model", MODEL);
            root.setAttribute("source", source); root.setAttribute("verified", Boolean.toString(verified));
            root.setAttribute("package", OFFICIAL_PACKAGE); root.setAttribute("color", color);
            root.setAttribute("sourceVersion", sourceVersion);
            root.setAttribute("sourceVersionCode", Long.toString(sourceVersionCode));
            root.setAttribute("signatureSha256", signatureSha256); root.setAttribute("deviceAddress", deviceAddress);
            for (Slot slot : slots.values()) {
                Element item = document.createElement("slot"); root.appendChild(item);
                item.setAttribute("name", slot.name()); item.setAttribute("resource", slot.resource());
                item.setAttribute("resourceSha256", slot.resourceSha256()); item.setAttribute("file", slot.file());
                item.setAttribute("sha256", slot.sha256()); item.setAttribute("width", Integer.toString(slot.width()));
                item.setAttribute("height", Integer.toString(slot.height()));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception error) { throw new IllegalStateException("Unable to encode artwork manifest", error); }
    }

    public boolean matchesOfficial(String packageName, long versionCode, Set<String> signingDigests,
            String requestedColor) {
        return verified && "official".equals(source) && OFFICIAL_PACKAGE.equals(packageName)
                && sourceVersionCode == versionCode && Az100ArtPolicy.sha256(signatureSha256)
                && signingDigests.stream().anyMatch(signatureSha256::equalsIgnoreCase)
                && (requestedColor == null || requestedColor.isBlank() || color.equals(requestedColor));
    }

    public static Az100ArtManifest read(InputStream input) throws IOException {
        try {
            // The byte bound and rejected declarations also protect Android parsers that lack a feature.
            byte[] bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            if (bytes.length > MAX_MANIFEST_BYTES) throw new IOException("Artwork manifest too large");
            String xml = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (xml.indexOf('\u0000') >= 0) throw new IOException("Artwork manifest must use UTF-8");
            if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY")) {
                throw new IOException("Artwork manifest declarations are forbidden");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setExpandEntityReferences(false);
            disableFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            disableFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            disableFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            disableFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw new org.xml.sax.SAXException("External entities forbidden"); });
            Element root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            if (!"az100-art".equals(root.getTagName())
                    || !"1".equals(root.getAttribute("schema"))
                    || !MODEL.equals(root.getAttribute("model"))) {
                throw new IOException("Unsupported artwork manifest");
            }
            String source = root.getAttribute("source");
            if (!Set.of("official", "manual", "bundled", "unverified").contains(source)) {
                throw new IOException("Invalid artwork source");
            }
            boolean verified = "true".equals(root.getAttribute("verified"));
            String color = root.getAttribute("color");
            if (color.isBlank() || color.length() > 64) throw new IOException("Invalid artwork color");
            String certificate = root.getAttribute("signatureSha256");
            long versionCode = number(root.getAttribute("sourceVersionCode"));
            Map<String, Slot> slots = new LinkedHashMap<>();
            for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element item)) continue;
                if (!"slot".equals(item.getTagName())) throw new IOException("Unknown artwork element");
                String name = item.getAttribute("name"), resource = item.getAttribute("resource");
                String resourceHash = item.getAttribute("resourceSha256"), file = item.getAttribute("file");
                String hash = item.getAttribute("sha256");
                if (!Az100ArtPolicy.validSlot(name) || slots.containsKey(name)
                        || (!file.isEmpty() && !Az100ArtPolicy.relativeFile(file))
                        || (!hash.isEmpty() && !Az100ArtPolicy.sha256(hash))) {
                    throw new IOException("Invalid artwork slot");
                }
                if ("official".equals(source) && (!Az100ArtPolicy.resourceName(resource)
                        || !Az100ArtPolicy.sha256(resourceHash))) {
                    throw new IOException("Official artwork requires exact resource and digest");
                }
                if (!"official".equals(source) && (file.isEmpty() || !Az100ArtPolicy.sha256(hash))) {
                    throw new IOException("Cached artwork requires exact file and digest");
                }
                long widthValue = number(item.getAttribute("width"));
                long heightValue = number(item.getAttribute("height"));
                if (widthValue < 0 || heightValue < 0 || widthValue > 32768 || heightValue > 32768) {
                    throw new IOException("Invalid artwork dimensions");
                }
                int width = (int) widthValue;
                int height = (int) heightValue;
                slots.put(name, new Slot(name, resource, resourceHash, file, hash, width, height));
            }
            if (verified && "official".equals(source) && (versionCode <= 0
                    || !Az100ArtPolicy.sha256(certificate) || slots.isEmpty()
                    || !OFFICIAL_PACKAGE.equals(root.getAttribute("package")))) {
                throw new IOException("Incomplete official artwork identity");
            }
            return new Az100ArtManifest(source, color, root.getAttribute("sourceVersion"), versionCode,
                    certificate, root.getAttribute("deviceAddress"), verified, slots);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Invalid artwork manifest", error);
        }
    }

    private static void disableFeature(DocumentBuilderFactory factory, String feature, boolean value) {
        try { factory.setFeature(feature, value); }
        catch (javax.xml.parsers.ParserConfigurationException ignored) {
            // Android's parser may omit a feature; UTF-8 preflight rejects all declarations.
        }
    }

    private static long number(String value) throws IOException {
        if (value.isEmpty()) return 0;
        try { return Long.parseLong(value); }
        catch (NumberFormatException error) { throw new IOException("Invalid artwork number", error); }
    }
}
