import AVFoundation
import UIKit
import Shared

/// Khung camera quét mã QR (AVCaptureSession + AVCaptureMetadataOutput).
/// Kotlin nhúng view này bằng UIKitView trong IosPlatformUi.QrCameraScanner.
final class QrScannerView: UIView, AVCaptureMetadataOutputObjectsDelegate {
    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "damthoai.qr.session")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let listener: QrCodeListener

    // Chống báo lặp cùng một mã liên tục khi camera vẫn nhìn thấy nó
    private var lastCode: String?
    private var lastCodeTime = Date.distantPast

    init(listener: QrCodeListener) {
        self.listener = listener
        super.init(frame: .zero)
        backgroundColor = .black
        configureSession()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) không được hỗ trợ")
    }

    private func configureSession() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input)
        else {
            listener.onError(message: "Không mở được camera")
            return
        }
        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            listener.onError(message: "Camera không hỗ trợ quét mã")
            return
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        layer.addSublayer(preview)
        previewLayer = preview

        let session = self.session
        sessionQueue.async { session.startRunning() }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }

    func stop() {
        let session = self.session
        sessionQueue.async { session.stopRunning() }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let code = object.stringValue
        else { return }
        let now = Date()
        if code == lastCode, now.timeIntervalSince(lastCodeTime) < 2 { return }
        lastCode = code
        lastCodeTime = now
        listener.onQrDetected(code: code)
    }
}
