package com.aulahub.backend.service;

import com.aulahub.backend.exception.AulaNoEncontradaException;
import com.aulahub.backend.exception.UsuarioNoEncontradoException;
import com.aulahub.backend.model.AulaEntity;
import com.aulahub.backend.model.ReservaEntity;
import com.aulahub.backend.model.UserEntity;
import com.aulahub.backend.repository.AulaRepository;
import com.aulahub.backend.repository.ReservaRepository;
import com.aulahub.backend.repository.UserRepository;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.util.CellRangeAddress;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private final ReservaRepository reservaRepository;
    private final AulaRepository aulaRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_HORA  = DateTimeFormatter.ofPattern("HH:mm");

    // ── PDF por Aula ────────────────────────────────────────────────────────────

    // Genera un PDF con las reservas de un aula.
    // Si se pasan desde/hasta filtra por ese rango; si son null exporta todo.
    @Transactional(readOnly = true)
    public byte[] generarPdfPorAula(Long aulaId, LocalDate desde, LocalDate hasta) {
        AulaEntity aula = aulaRepository.findById(aulaId)
                .orElseThrow(() -> new AulaNoEncontradaException("Aula con id " + aulaId + " no encontrada"));

        List<ReservaEntity> reservas = (desde != null && hasta != null)
                ? reservaRepository.findByAula_IdAndFechaBetweenOrderByFechaAscHoraInicioAsc(aulaId, desde, hasta)
                : reservaRepository.findByAula_IdOrderByFechaAscHoraInicioAsc(aulaId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 50, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // Título
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, new BaseColor(33, 33, 33));
            Font subFont   = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL, BaseColor.DARK_GRAY);
            Font cellFont  = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL);
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, BaseColor.WHITE);

            Paragraph titulo = new Paragraph("Reservas — " + aula.getNombre()
                    + " (" + aula.getCodigo() + ")", titleFont);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph subtitulo = new Paragraph(
                    "Facultad: " + aula.getFacultad().getNombre()
                    + "   |   Generado: " + java.time.LocalDate.now().format(FMT_FECHA), subFont);
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            subtitulo.setSpacingAfter(12f);
            doc.add(subtitulo);

            // Tabla
            String[] headers = {"Fecha", "Hora inicio", "Hora fin", "Turno",
                                 "Solicitante", "Tipo de reserva", "Materia / Grupo", "Estado"};
            PdfPTable tabla = crearTabla(headers.length, headers, headerFont);

            for (ReservaEntity r : reservas) {
                tabla.addCell(celda(r.getFecha().format(FMT_FECHA), cellFont));
                tabla.addCell(celda(r.getHoraInicio().format(FMT_HORA), cellFont));
                tabla.addCell(celda(r.getHoraFin().format(FMT_HORA), cellFont));
                tabla.addCell(celda(r.getTurno().name(), cellFont));
                tabla.addCell(celda(r.getSolicitante().getNombre(), cellFont));
                tabla.addCell(celda(r.getTipoReserva().getNombre(), cellFont));
                String materiaGrupo = "";
                if (r.getMateria() != null) materiaGrupo = r.getMateria().getNombre();
                if (r.getGrupo()   != null) materiaGrupo += (materiaGrupo.isEmpty() ? "" : " / ") + r.getGrupo();
                tabla.addCell(celda(materiaGrupo, cellFont));
                tabla.addCell(celdaEstado(r.getStatus().name(), cellFont));
            }

            if (reservas.isEmpty()) {
                PdfPCell vacia = new PdfPCell(new Phrase("Sin reservas registradas", cellFont));
                vacia.setColspan(headers.length);
                vacia.setHorizontalAlignment(Element.ALIGN_CENTER);
                vacia.setPadding(8f);
                tabla.addCell(vacia);
            }

            doc.add(tabla);
            doc.close();
            return baos.toByteArray();

        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Error generando PDF por aula: " + e.getMessage(), e);
        }
    }

    // ── PDF por Profesor ────────────────────────────────────────────────────────

    // Genera un PDF con las reservas de un profesor.
    // Si se pasan desde/hasta filtra por ese rango; si son null exporta todo.
    @Transactional(readOnly = true)
    public byte[] generarPdfPorProfesor(Long usuarioId, LocalDate desde, LocalDate hasta) {
        UserEntity profesor = userRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNoEncontradoException(
                        "Usuario con id " + usuarioId + " no encontrado"));

        List<ReservaEntity> reservas = (desde != null && hasta != null)
                ? reservaRepository.findBySolicitante_IdAndFechaBetweenOrderByFechaAscHoraInicioAsc(usuarioId, desde, hasta)
                : reservaRepository.findBySolicitante_IdOrderByFechaAscHoraInicioAsc(usuarioId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 36, 36, 50, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont  = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, new BaseColor(33, 33, 33));
            Font subFont    = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL, BaseColor.DARK_GRAY);
            Font cellFont   = new Font(Font.FontFamily.HELVETICA, 9,  Font.NORMAL);
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 9,  Font.BOLD, BaseColor.WHITE);

            Paragraph titulo = new Paragraph(
                    "Reservas — " + profesor.getNombre(), titleFont);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph subtitulo = new Paragraph(
                    "Email: " + profesor.getEmail()
                    + "   |   Generado: " + java.time.LocalDate.now().format(FMT_FECHA), subFont);
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            subtitulo.setSpacingAfter(12f);
            doc.add(subtitulo);

            String[] headers = {"Aula", "Facultad", "Fecha", "Hora inicio", "Hora fin",
                                 "Turno", "Tipo de reserva", "Materia / Grupo", "Estado"};
            PdfPTable tabla = crearTabla(headers.length, headers, headerFont);

            for (ReservaEntity r : reservas) {
                tabla.addCell(celda(r.getAula().getCodigo() + " — " + r.getAula().getNombre(), cellFont));
                tabla.addCell(celda(r.getAula().getFacultad().getNombre(), cellFont));
                tabla.addCell(celda(r.getFecha().format(FMT_FECHA), cellFont));
                tabla.addCell(celda(r.getHoraInicio().format(FMT_HORA), cellFont));
                tabla.addCell(celda(r.getHoraFin().format(FMT_HORA), cellFont));
                tabla.addCell(celda(r.getTurno().name(), cellFont));
                tabla.addCell(celda(r.getTipoReserva().getNombre(), cellFont));
                String materiaGrupo = "";
                if (r.getMateria() != null) materiaGrupo = r.getMateria().getNombre();
                if (r.getGrupo()   != null) materiaGrupo += (materiaGrupo.isEmpty() ? "" : " / ") + r.getGrupo();
                tabla.addCell(celda(materiaGrupo, cellFont));
                tabla.addCell(celdaEstado(r.getStatus().name(), cellFont));
            }

            if (reservas.isEmpty()) {
                PdfPCell vacia = new PdfPCell(new Phrase("Sin reservas registradas", cellFont));
                vacia.setColspan(headers.length);
                vacia.setHorizontalAlignment(Element.ALIGN_CENTER);
                vacia.setPadding(8f);
                tabla.addCell(vacia);
            }

            doc.add(tabla);
            doc.close();
            return baos.toByteArray();

        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Error generando PDF por profesor: " + e.getMessage(), e);
        }
    }

    // ── Excel todas las reservas ────────────────────────────────────────────────

    // Genera un Excel con las reservas del sistema.
    // Si se pasan desde/hasta filtra por ese rango; si son null exporta todo.
    @Transactional(readOnly = true)
    public byte[] generarExcelTodasLasReservas(LocalDate desde, LocalDate hasta) {
        List<ReservaEntity> reservas = (desde != null && hasta != null)
                ? reservaRepository.findByFechaBetweenOrderByFechaAscHoraInicioAsc(desde, hasta)
                : reservaRepository.findAll();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Reservas");

            // Estilo de encabezado
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 10);
            headerStyle.setFont(headerFont);

            // Estilo de fila alternada
            CellStyle altStyle = workbook.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Encabezados
            String[] headers = {
                "ID", "Aula", "Facultad", "Solicitante", "Email",
                "Tipo de reserva", "Materia", "Grupo", "Turno",
                "Fecha", "Hora inicio", "Hora fin", "Estado", "Motivo rechazo"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Filas de datos
            int rowNum = 1;
            for (ReservaEntity r : reservas) {
                Row row = sheet.createRow(rowNum);

                if (rowNum % 2 == 0) {
                    for (int i = 0; i < headers.length; i++) {
                        row.createCell(i).setCellStyle(altStyle);
                    }
                }

                row.createCell(0).setCellValue(r.getId());
                row.createCell(1).setCellValue(r.getAula().getCodigo() + " — " + r.getAula().getNombre());
                row.createCell(2).setCellValue(r.getAula().getFacultad().getNombre());
                row.createCell(3).setCellValue(r.getSolicitante().getNombre());
                row.createCell(4).setCellValue(r.getSolicitante().getEmail());
                row.createCell(5).setCellValue(r.getTipoReserva().getNombre());
                row.createCell(6).setCellValue(r.getMateria() != null ? r.getMateria().getNombre() : "");
                row.createCell(7).setCellValue(r.getGrupo() != null ? r.getGrupo() : "");
                row.createCell(8).setCellValue(r.getTurno().name());
                row.createCell(9).setCellValue(r.getFecha().format(FMT_FECHA));
                row.createCell(10).setCellValue(r.getHoraInicio().format(FMT_HORA));
                row.createCell(11).setCellValue(r.getHoraFin().format(FMT_HORA));
                row.createCell(12).setCellValue(r.getStatus().name());
                row.createCell(13).setCellValue(r.getMotivoRechazo() != null ? r.getMotivoRechazo() : "");

                rowNum++;
            }
            // Filtros en los encabezados
            sheet.setAutoFilter(new CellRangeAddress(
                    0,
                    Math.max(rowNum - 1, 0),
                    0,
                    headers.length - 1
            ));

            // Mantiene visibles los encabezados al bajar
            sheet.createFreezePane(0, 1);

            // Autoajuste de columnas
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de reservas: " + e.getMessage(), e);
        }
    }

    // ── helpers PDF ─────────────────────────────────────────────────────────────

    private PdfPTable crearTabla(int columnas, String[] headers, Font headerFont) throws DocumentException {
        PdfPTable tabla = new PdfPTable(columnas);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(4f);

        BaseColor headerBg = new BaseColor(21, 101, 192);   // azul material
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6f);
            tabla.addCell(cell);
        }
        return tabla;
    }

    private PdfPCell celda(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        cell.setPadding(5f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private PdfPCell celdaEstado(String estado, Font baseFont) {
        BaseColor color = switch (estado) {
            case "ACEPTADA"  -> new BaseColor(200, 230, 201);   // verde suave
            case "RECHAZADA" -> new BaseColor(255, 205, 210);   // rojo suave
            case "CANCELADA" -> new BaseColor(255, 224, 178);   // naranja suave
            default           -> new BaseColor(255, 249, 196);  // amarillo suave (PENDIENTE)
        };
        Font font = new Font(baseFont.getFamily(), baseFont.getSize(), Font.BOLD, baseFont.getColor());
        PdfPCell cell = new PdfPCell(new Phrase(estado, font));
        cell.setBackgroundColor(color);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5f);
        return cell;
    }
}
