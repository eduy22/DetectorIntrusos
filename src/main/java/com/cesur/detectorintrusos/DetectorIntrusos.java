
package com.cesur.detectorintrusos;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

public class DetectorIntrusos {

    public static void main(String[] args) {

        // 1. Cargar OpenCV
        OpenCV.loadLocally();

        // 2. Cargar el clasificador facial
        Path archivoTemporal = null;
        CascadeClassifier detector =
                new CascadeClassifier();

        try (InputStream recurso =
                DetectorIntrusos.class.getResourceAsStream(
                    "/haarcascade_frontalface_default.xml"
                )) {

            if (recurso == null) {
                System.out.println(
                    "ERROR: No se encuentra el clasificador XML."
                );
                return;
            }

            archivoTemporal = Files.createTempFile(
                "clasificador_rostros_", ".xml"
            );

            Files.copy(
                recurso,
                archivoTemporal,
                StandardCopyOption.REPLACE_EXISTING
            );

            if (!detector.load(archivoTemporal.toString())) {
                System.out.println(
                    "ERROR: No se pudo cargar el clasificador."
                );
                return;
            }

        } catch (IOException e) {
            System.out.println(
                "Error al cargar el XML: " + e.getMessage()
            );
            return;

        } finally {
            if (archivoTemporal != null) {
                try {
                    Files.deleteIfExists(archivoTemporal);
                } catch (IOException e) {
                    System.out.println(
                        "No se pudo borrar el archivo temporal."
                    );
                }
            }
        }

        System.out.println("Clasificador facial cargado.");

        // 3. Abrir la webcam
        VideoCapture camara = new VideoCapture(0);

        if (!camara.isOpened()) {
            System.out.println(
                "ERROR: No se ha podido abrir la cámara."
            );
            camara.release();
            return;
        }

        System.out.println("Cámara abierta correctamente.");

        // 4. Crear la ventana gráfica
        JFrame ventana = new JFrame(
            "Sistema de detección de intrusos"
        );

        JLabel etiquetaVideo = new JLabel();
        ventana.add(etiquetaVideo);

        ventana.setSize(660, 520);
        ventana.setLocationRelativeTo(null);
        ventana.setDefaultCloseOperation(
            JFrame.DISPOSE_ON_CLOSE
        );

        AtomicBoolean ejecutando =
                new AtomicBoolean(true);

        ventana.addWindowListener(
            new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(
                    java.awt.event.WindowEvent e
                ) {
                    ejecutando.set(false);
                }
            }
        );

        ventana.setVisible(true);

        // 5. Preparar las imágenes
        Mat fotograma = new Mat();
        Mat imagenGris = new Mat();
        MatOfRect rostros = new MatOfRect();
        org.opencv.core.MatOfByte bytesImagen =
                new org.opencv.core.MatOfByte();

        boolean intrusoAnterior = false;

        try {
            // 6. Captura continua
            while (ejecutando.get()) {

                if (!camara.read(fotograma)
                        || fotograma.empty()) {
                    System.out.println(
                        "No se pudo capturar un fotograma."
                    );
                    break;
                }

                // 7. Convertir a escala de grises
                Imgproc.cvtColor(
                    fotograma,
                    imagenGris,
                    Imgproc.COLOR_BGR2GRAY
                );

                Imgproc.equalizeHist(
                    imagenGris,
                    imagenGris
                );

                // 8. Detectar rostros
                detector.detectMultiScale(
                    imagenGris,
                    rostros,
                    1.1,
                    5,
                    0,
                    new Size(50, 50),
                    new Size()
                );

                Rect[] carasDetectadas =
                        rostros.toArray();

                boolean hayIntruso =
                        carasDetectadas.length > 0;

                // 9. Dibujar los rectángulos
                for (Rect cara : carasDetectadas) {

                    Imgproc.rectangle(
                        fotograma,
                        new Point(cara.x, cara.y),
                        new Point(
                            cara.x + cara.width,
                            cara.y + cara.height
                        ),
                        new Scalar(0, 255, 0),
                        2
                    );
                }

                // 10. Mostrar alerta
                if (hayIntruso) {

                    Imgproc.putText(
                        fotograma,
                        "INTRUSO",
                        new Point(30, 50),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        1.3,
                        new Scalar(0, 0, 255),
                        3
                    );

                    if (!intrusoAnterior) {
                        System.out.println("INTRUSO");
                    }
                }

                intrusoAnterior = hayIntruso;

                // 11. Convertir imagen para Swing
                org.opencv.core.MatOfInt parametros =
                        new org.opencv.core.MatOfInt();

                try {
                    if (!Imgcodecs.imencode(
                            ".jpg",
                            fotograma,
                            bytesImagen,
                            parametros
                    )) {
                        continue;
                    }
                } finally {
                    parametros.release();
                }

                byte[] datos = bytesImagen.toArray();

                BufferedImage imagen = ImageIO.read(
                    new ByteArrayInputStream(datos)
                );

                if (imagen != null) {
                    ImageIcon icono = new ImageIcon(imagen);

                    SwingUtilities.invokeLater(() -> {
                        if (ejecutando.get()) {
                            etiquetaVideo.setIcon(icono);
                        }
                    });
                }

                Thread.sleep(30);
            }

        } catch (IOException e) {
            System.out.println(
                "Error de imagen: " + e.getMessage()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

        } finally {

            // 12. Liberar los recursos
            camara.release();
            fotograma.release();
            imagenGris.release();
            rostros.release();
            bytesImagen.release();

            SwingUtilities.invokeLater(
                ventana::dispose
            );

            System.out.println("Cámara liberada.");
        }
    }
}
