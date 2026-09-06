import AppKit
import Foundation
import UniformTypeIdentifiers

final class PasswordFieldPair: NSObject {
    let stack = NSStackView()
    let secure = NSSecureTextField()
    let plain = NSTextField()
    private let toggle = NSButton(checkboxWithTitle: "Mostra", target: nil, action: nil)

    override init() {
        super.init()
        stack.orientation = .horizontal
        stack.spacing = 8
        secure.placeholderString = "Password"
        plain.placeholderString = "Password"
        plain.isHidden = true
        secure.setContentHuggingPriority(.defaultLow, for: .horizontal)
        plain.setContentHuggingPriority(.defaultLow, for: .horizontal)
        toggle.target = self
        toggle.action = #selector(toggleVisibility)
        stack.addArrangedSubview(secure)
        stack.addArrangedSubview(plain)
        stack.addArrangedSubview(toggle)
    }

    var value: String {
        get { plain.isHidden ? secure.stringValue : plain.stringValue }
        set { secure.stringValue = newValue; plain.stringValue = newValue }
    }

    @objc private func toggleVisibility() {
        if toggle.state == .on {
            plain.stringValue = secure.stringValue
            secure.isHidden = true
            plain.isHidden = false
        } else {
            secure.stringValue = plain.stringValue
            plain.isHidden = true
            secure.isHidden = false
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    private let maxFileBytes = 100 * 1024 * 1024
    private var window: NSWindow!
    private var originalData: Data?
    private var originalName = ""
    private var originalMime = "application/octet-stream"
    private var loadedContainer: Data?
    private var loadedInfo: Fra1ContainerInfo?
    private var restored: Fra1OriginalFile?

    private let originalInfo = NSTextField(labelWithString: "Nessun file scelto")
    private let originalStatus = NSTextField(labelWithString: "")
    private let restoreInfo = NSTextField(labelWithString: "Nessun FRA1/FRA1E scelto")
    private let restoreStatus = NSTextField(labelWithString: "")
    private let encryptPassword = PasswordFieldPair()
    private let decryptPassword = PasswordFieldPair()
    private let generatedPassword = NSTextField()
    private let textInput = NSTextField()
    private let fractionInput = NSTextField()
    private var decryptButton: NSButton!
    private var saveOriginalButton: NSButton!

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        buildWindow()
        window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

    private func buildWindow() {
        window = NSWindow(
            contentRect: NSRect(x: 0, y: 0, width: 760, height: 760),
            styleMask: [.titled, .closable, .miniaturizable, .resizable],
            backing: .buffered,
            defer: false
        )
        window.title = "Frazione Lab Offline"
        window.center()

        let scroll = NSScrollView()
        scroll.hasVerticalScroller = true
        scroll.drawsBackground = false
        scroll.translatesAutoresizingMaskIntoConstraints = false

        let document = NSView()
        document.translatesAutoresizingMaskIntoConstraints = false
        scroll.documentView = document

        let stack = NSStackView()
        stack.orientation = .vertical
        stack.alignment = .leading
        stack.spacing = 10
        stack.translatesAutoresizingMaskIntoConstraints = false
        document.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: document.leadingAnchor, constant: 22),
            stack.trailingAnchor.constraint(equalTo: document.trailingAnchor, constant: -22),
            stack.topAnchor.constraint(equalTo: document.topAnchor, constant: 22),
            stack.bottomAnchor.constraint(equalTo: document.bottomAnchor, constant: -22),
            document.widthAnchor.constraint(equalTo: scroll.contentView.widthAnchor)
        ])

        let root = NSView()
        root.translatesAutoresizingMaskIntoConstraints = false
        root.addSubview(scroll)
        NSLayoutConstraint.activate([
            scroll.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: root.topAnchor),
            scroll.bottomAnchor.constraint(equalTo: root.bottomAnchor)
        ])
        window.contentView = root

        stack.addArrangedSubview(titleLabel("Frazione Lab"))
        stack.addArrangedSubview(label("Offline • FRA1 lossless • FRA1E AES-128/256 • nessun upload"))
        addSeparator(to: stack)

        stack.addArrangedSubview(sectionLabel("Foto originale → FRA1 / FRA1E"))
        stack.addArrangedSubview(label("La foto viene letta byte-per-byte. Nessun ridimensionamento o ricampionamento."))
        stack.addArrangedSubview(button("SCEGLI FOTO ORIGINALE", #selector(chooseOriginal)))
        stack.addArrangedSubview(originalInfo)
        stack.addArrangedSubview(button("CREA E SALVA .FRA1", #selector(createPlainFra1)))
        stack.addArrangedSubview(label("Password FRA1E (non viene salvata):"))
        stretch(encryptPassword.stack)
        stack.addArrangedSubview(encryptPassword.stack)
        let cryptoRow = NSStackView(views: [
            button("FRA1E AES-128", #selector(createFra1e128)),
            button("FRA1E AES-256", #selector(createFra1e256))
        ])
        cryptoRow.orientation = .horizontal
        cryptoRow.spacing = 8
        stack.addArrangedSubview(cryptoRow)
        stack.addArrangedSubview(label("PBKDF2-SHA-256: 600.000 iterazioni • overhead FRA1E: 64 byte."))
        styleStatus(originalStatus)
        stack.addArrangedSubview(originalStatus)

        addSeparator(to: stack)
        stack.addArrangedSubview(sectionLabel("Generatore password sicura — solo Mac offline"))
        generatedPassword.isEditable = false
        generatedPassword.isSelectable = true
        generatedPassword.placeholderString = "Password generata"
        stretch(generatedPassword)
        stack.addArrangedSubview(generatedPassword)
        let genRow = NSStackView(views: [
            button("GENERA 16", #selector(generate16)),
            button("GENERA 24", #selector(generate24)),
            button("GENERA 32", #selector(generate32)),
            button("COPIA", #selector(copyGeneratedPassword))
        ])
        genRow.orientation = .horizontal
        genRow.spacing = 8
        stack.addArrangedSubview(genRow)
        stack.addArrangedSubview(label("La password viene generata con il generatore crittografico di macOS e non viene memorizzata dall'app."))

        addSeparator(to: stack)
        stack.addArrangedSubview(sectionLabel("FRA1 / FRA1E → recupera originale"))
        stack.addArrangedSubview(label("Il formato e AES-128/AES-256 vengono riconosciuti automaticamente dall'intestazione interna."))
        stack.addArrangedSubview(button("SCEGLI FILE .FRA1 / .FRA1E", #selector(chooseContainer)))
        stack.addArrangedSubview(restoreInfo)
        stretch(decryptPassword.stack)
        stack.addArrangedSubview(decryptPassword.stack)
        decryptButton = button("DECIFRA FRA1E", #selector(decryptContainer))
        decryptButton.isEnabled = false
        stack.addArrangedSubview(decryptButton)
        saveOriginalButton = button("SALVA ORIGINALE", #selector(saveRestoredOriginal))
        saveOriginalButton.isEnabled = false
        stack.addArrangedSubview(saveOriginalButton)
        styleStatus(restoreStatus)
        stack.addArrangedSubview(restoreStatus)

        addSeparator(to: stack)
        stack.addArrangedSubview(sectionLabel("Testo ⇄ frazione"))
        textInput.placeholderString = "Scrivi un testo"
        fractionInput.placeholderString = "N / 2^K"
        stretch(textInput)
        stretch(fractionInput)
        stack.addArrangedSubview(textInput)
        let textRow = NSStackView(views: [
            button("CODIFICA", #selector(encodeText)),
            button("DECODIFICA", #selector(decodeText))
        ])
        textRow.orientation = .horizontal
        textRow.spacing = 8
        stack.addArrangedSubview(textRow)
        stack.addArrangedSubview(fractionInput)

        addSeparator(to: stack)
        let privacy = label("PRIVACY: l'app è offline. File e password restano sul Mac salvo quando scegli tu dove salvare i risultati.")
        privacy.font = .boldSystemFont(ofSize: 12)
        stack.addArrangedSubview(privacy)
    }

    @objc private func chooseOriginal() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        if #available(macOS 11.0, *) { panel.allowedContentTypes = [.image] }
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            let values = try url.resourceValues(forKeys: [.fileSizeKey])
            if let size = values.fileSize, size > maxFileBytes { throw Fra1Error.invalid("File oltre 100 MB") }
            let data = try Data(contentsOf: url)
            if data.count > maxFileBytes { throw Fra1Error.invalid("File oltre 100 MB") }
            originalData = data
            originalName = url.lastPathComponent
            originalMime = mimeType(for: url)
            originalInfo.stringValue = "\(originalName) • \(sizeText(data.count)) • \(originalMime)"
            setStatus(originalStatus, "Pronta • SHA-256 \(String(Fra1Codec.sha256Hex(data).prefix(20)))…", error: false)
        } catch { setStatus(originalStatus, message(error), error: true) }
    }

    @objc private func createPlainFra1() { createContainer(encrypted: false, keyBits: 0) }
    @objc private func createFra1e128() { createContainer(encrypted: true, keyBits: 128) }
    @objc private func createFra1e256() { createContainer(encrypted: true, keyBits: 256) }

    private func createContainer(encrypted: Bool, keyBits: Int) {
        guard let data = originalData else { setStatus(originalStatus, "Scegli prima una foto.", error: true); return }
        let password = encryptPassword.value
        if encrypted && password.isEmpty { setStatus(originalStatus, "Inserisci una password per FRA1E.", error: true); return }
        setStatus(originalStatus, encrypted ? "Cifratura AES-\(keyBits) in corso…" : "Creo FRA1…", error: false)
        let name = originalName
        let mime = originalMime
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let fra1 = try Fra1Codec.packOriginalFile(name: name, mime: mime, bytes: data)
                let output = encrypted ? try Fra1Codec.encryptFra1(fra1, password: password, keyBits: keyBits) : fra1
                let fileName = name + (encrypted ? ".fra1e" : ".fra1")
                DispatchQueue.main.async {
                    let overhead = output.count - fra1.count
                    if encrypted {
                        self.setStatus(self.originalStatus, "Creato AES-\(keyBits) • originale \(self.sizeText(data.count)) • FRA1 \(self.sizeText(fra1.count)) • FRA1E \(self.sizeText(output.count)) • +\(overhead) byte.", error: false)
                    } else {
                        self.setStatus(self.originalStatus, "FRA1 creato • originale \(self.sizeText(data.count)) • FRA1 \(self.sizeText(fra1.count)).", error: false)
                    }
                    self.saveData(output, suggestedName: fileName)
                }
            } catch { DispatchQueue.main.async { self.setStatus(self.originalStatus, self.message(error), error: true) } }
        }
    }

    @objc private func chooseContainer() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            let data = try Data(contentsOf: url)
            if data.count > maxFileBytes + 1024 * 1024 { throw Fra1Error.invalid("FRA1/FRA1E oltre il limite prudenziale") }
            let info = try Fra1Codec.inspectContainer(data)
            guard info.kind != "unknown" else { throw Fra1Error.invalid("File non riconosciuto come FRA1/FRA1E") }
            loadedContainer = data
            loadedInfo = info
            restored = nil
            saveOriginalButton.isEnabled = false
            if info.encrypted {
                restoreInfo.stringValue = "\(url.lastPathComponent) • \(sizeText(data.count)) • FRA1E AES-\(info.keyBits)"
                decryptButton.isEnabled = true
                setStatus(restoreStatus, "AES-\(info.keyBits) riconosciuto automaticamente • PBKDF2 \(info.iterations) iterazioni. Inserisci la password.", error: false)
            } else {
                decryptButton.isEnabled = false
                let file = try Fra1Codec.unpackOriginalFile(data)
                restored = file
                showRestored(file, prefix: "FRA1 normale • SHA-256 verificato ✓")
            }
        } catch { setStatus(restoreStatus, message(error), error: true) }
    }

    @objc private func decryptContainer() {
        guard let data = loadedContainer, let info = loadedInfo, info.encrypted else { return }
        let password = decryptPassword.value
        if password.isEmpty { setStatus(restoreStatus, "Inserisci la password.", error: true); return }
        decryptButton.isEnabled = false
        setStatus(restoreStatus, "Decifro AES-\(info.keyBits)…", error: false)
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let fra1 = try Fra1Codec.decryptFra1(data, password: password)
                let file = try Fra1Codec.unpackOriginalFile(fra1)
                DispatchQueue.main.async {
                    self.decryptButton.isEnabled = true
                    self.restored = file
                    self.showRestored(file, prefix: "Password corretta • AES-\(info.keyBits) decifrato • SHA-256 verificato ✓")
                }
            } catch {
                DispatchQueue.main.async {
                    self.decryptButton.isEnabled = true
                    self.restored = nil
                    self.saveOriginalButton.isEnabled = false
                    self.setStatus(self.restoreStatus, self.message(error), error: true)
                }
            }
        }
    }

    private func showRestored(_ file: Fra1OriginalFile, prefix: String) {
        restoreInfo.stringValue = "\(file.name) • \(sizeText(file.bytes.count)) • \(file.mime)"
        saveOriginalButton.isEnabled = true
        setStatus(restoreStatus, "\(prefix) • file originale pronto da salvare byte-per-byte.", error: false)
    }

    @objc private func saveRestoredOriginal() {
        guard let file = restored else { return }
        saveData(file.bytes, suggestedName: file.name)
    }

    @objc private func generate16() { generatePassword(16) }
    @objc private func generate24() { generatePassword(24) }
    @objc private func generate32() { generatePassword(32) }

    private func generatePassword(_ length: Int) {
        do { generatedPassword.stringValue = try PasswordGenerator.generate(length: length) }
        catch { generatedPassword.stringValue = "Errore: \(message(error))" }
    }

    @objc private func copyGeneratedPassword() {
        guard !generatedPassword.stringValue.isEmpty else { return }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(generatedPassword.stringValue, forType: .string)
    }

    @objc private func encodeText() {
        do { fractionInput.stringValue = try Fra1Codec.encodeTextFraction(textInput.stringValue) }
        catch { fractionInput.stringValue = "Errore: \(message(error))" }
    }

    @objc private func decodeText() {
        do { textInput.stringValue = try Fra1Codec.decodeTextFraction(fractionInput.stringValue) }
        catch { textInput.stringValue = "Errore: \(message(error))" }
    }

    private func saveData(_ data: Data, suggestedName: String) {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = suggestedName
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do { try data.write(to: url, options: .atomic) }
        catch { let alert = NSAlert(); alert.messageText = "Salvataggio fallito"; alert.informativeText = message(error); alert.runModal() }
    }

    private func mimeType(for url: URL) -> String {
        if #available(macOS 11.0, *), let type = UTType(filenameExtension: url.pathExtension), let mime = type.preferredMIMEType { return mime }
        return "application/octet-stream"
    }

    private func sizeText(_ n: Int) -> String {
        if n < 1024 { return "\(n) B" }
        if n < 1024 * 1024 { return String(format: "%.1f KB", Double(n) / 1024.0) }
        return String(format: "%.2f MB", Double(n) / 1024.0 / 1024.0)
    }

    private func message(_ error: Error) -> String { (error as? LocalizedError)?.errorDescription ?? error.localizedDescription }

    private func setStatus(_ field: NSTextField, _ text: String, error: Bool) {
        field.stringValue = text
        field.textColor = error ? .systemRed : .systemGreen
    }

    private func titleLabel(_ text: String) -> NSTextField {
        let field = NSTextField(labelWithString: text)
        field.font = .boldSystemFont(ofSize: 30)
        return field
    }

    private func sectionLabel(_ text: String) -> NSTextField {
        let field = NSTextField(labelWithString: text)
        field.font = .boldSystemFont(ofSize: 18)
        return field
    }

    private func label(_ text: String) -> NSTextField {
        let field = NSTextField(wrappingLabelWithString: text)
        field.font = .systemFont(ofSize: 12)
        return field
    }

    private func styleStatus(_ field: NSTextField) {
        field.font = .boldSystemFont(ofSize: 12)
        field.maximumNumberOfLines = 0
        field.lineBreakMode = .byWordWrapping
    }

    private func button(_ title: String, _ action: Selector) -> NSButton {
        let b = NSButton(title: title, target: self, action: action)
        b.bezelStyle = .rounded
        return b
    }

    private func stretch(_ view: NSView) {
        view.translatesAutoresizingMaskIntoConstraints = false
        view.widthAnchor.constraint(greaterThanOrEqualToConstant: 620).isActive = true
    }

    private func addSeparator(to stack: NSStackView) {
        let line = NSBox()
        line.boxType = .separator
        line.translatesAutoresizingMaskIntoConstraints = false
        line.widthAnchor.constraint(greaterThanOrEqualToConstant: 650).isActive = true
        stack.addArrangedSubview(line)
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.run()
