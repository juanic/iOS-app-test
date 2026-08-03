import SwiftUI

struct ReportView: View {
    let record: SessionRecord
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                headerCard
                StatokinesigramCard(record: record)
                TemporalSeriesCard(record: record)
                ParamsTable(metrics: record)
                InterpretationCard()
            }
            .padding()
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("Informe de estabilometría")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Cerrar") { dismiss() }
            }
        }
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(record.deviceName)
                .font(.headline)
                .foregroundColor(.primary)
            HStack {
                headerCaption("Fecha", record.dateText)
                Spacer()
                headerCaption("Duración", String(format: "%.1f s", record.durationSeconds))
                Spacer()
                headerCaption("Puntos", "\(record.pointCount)")
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemGroupedBackground)))
    }

    private func headerCaption(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.body)
                .bold()
        }
    }
}

private struct StatokinesigramCard: View {
    let record: SessionRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Statokinesigrama")
                .font(.subheadline)
                .bold()
            StatogramCanvas(xs: record.xs, ys: record.ys, ellipse: record.ellipse)
                .frame(height: 240)
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemGroupedBackground)))
    }
}

private struct TemporalSeriesCard: View {
    let record: SessionRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Series temporales")
                .font(.subheadline)
                .bold()
            HStack(spacing: 12) {
                Text("X (ML)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .frame(width: 48, alignment: .leading)
                TemporalSeriesCanvas(values: record.xs, timestamps: record.ts, color: .green)
                    .frame(height: 64)
            }
            HStack(spacing: 12) {
                Text("Y (AP)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .frame(width: 48, alignment: .leading)
                TemporalSeriesCanvas(values: record.ys, timestamps: record.ts, color: .orange)
                    .frame(height: 64)
            }
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemGroupedBackground)))
    }
}

private struct ParamsTable: View {
    let metrics: SessionRecord

    private struct Row: Identifiable {
        let id = UUID()
        let name: String
        let value: String
        let unit: String
    }

    private struct Category: Identifiable {
        let id = UUID()
        let title: String
        let accent: Color
        let rows: [Row]
    }

    private var categories: [Category] {
        let m = metrics
        return [
            Category(title: "Oscilación", accent: .green, rows: [
                Row(name: "Trayecto del COP", value: fmt1(m.swayPathLengthMm), unit: "mm"),
                Row(name: "Área elipse 95%", value: fmt1(m.ellipseAreaMm2), unit: "mm²"),
                Row(name: "Rango ML (X)", value: fmt1(m.rangeXmm), unit: "mm"),
                Row(name: "Rango AP (Y)", value: fmt1(m.rangeYmm), unit: "mm"),
                Row(name: "RMS ML (X)", value: fmt1(m.rmsXmm), unit: "mm"),
                Row(name: "RMS AP (Y)", value: fmt1(m.rmsYmm), unit: "mm")
            ]),
            Category(title: "Velocidad y frecuencia", accent: .orange, rows: [
                Row(name: "Velocidad media del COP", value: fmt1(m.meanVelocityMmS), unit: "mm/s"),
                Row(name: "Frecuencia media ML (X)", value: fmtFreq(m.meanFreqXHz), unit: "Hz"),
                Row(name: "Frecuencia media AP (Y)", value: fmtFreq(m.meanFreqYHz), unit: "Hz")
            ]),
            Category(title: "Distribución de carga", accent: .green, rows: [
                Row(name: "S1 promedio", value: fmt1(m.s1Avg), unit: "kg"),
                Row(name: "S2 promedio", value: fmt1(m.s2Avg), unit: "kg"),
                Row(name: "S3 promedio", value: fmt1(m.s3Avg), unit: "kg"),
                Row(name: "S1 máx / mín", value: "\(fmt1(m.s1Max)) / \(fmt1(m.s1Min))", unit: "kg"),
                Row(name: "S2 máx / mín", value: "\(fmt1(m.s2Max)) / \(fmt1(m.s2Min))", unit: "kg"),
                Row(name: "S3 máx / mín", value: "\(fmt1(m.s3Max)) / \(fmt1(m.s3Min))", unit: "kg"),
                Row(name: "Peso promedio", value: fmt1(m.totalAvgLoad), unit: "kg")
            ])
        ]
    }

    var body: some View {
        VStack(spacing: 10) {
            ForEach(categories) { category in
                VStack(spacing: 0) {
                    Text(category.title.uppercased())
                        .font(.caption)
                        .bold()
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(category.accent)
                    ForEach(category.rows) { row in
                        HStack {
                            Text(row.name)
                                .font(.footnote)
                                .foregroundColor(.primary)
                            Spacer()
                            Text(row.value)
                                .font(.footnote)
                                .bold()
                                .foregroundColor(category.accent)
                            Text(row.unit)
                                .font(.caption2)
                                .foregroundColor(.secondary)
                                .frame(width: 34, alignment: .trailing)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 9)
                    }
                }
                .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemGroupedBackground)))
            }
        }
    }

    private func fmt1(_ v: Double) -> String { String(format: "%.1f", v) }
    private func fmtFreq(_ v: Double?) -> String { v.map { String(format: "%.2f", $0) } ?? "--" }
}

private struct InterpretationCard: View {
    @State private var expanded = false

    var body: some View {
        DisclosureGroup(isExpanded: $expanded) {
            VStack(alignment: .leading, spacing: 10) {
                interpretationEntry(
                    "Trayecto del COP",
                    "Suma de distancias entre puntos COP consecutivos (mm). Refleja el balanceo total: mayor trayecto indica mayor inestabilidad."
                )
                interpretationEntry(
                    "Área elipse 95%",
                    "Elipse de confianza sobre la distribución de puntos COP (matriz de covarianza). Un área amplia sugiere dificultad para mantener el centro estable."
                )
                interpretationEntry(
                    "Rango y RMS (ML / AP)",
                    "Rango = max − min del COP por eje; RMS = √(Σ(x−x̄)²/n). Miden desplazamiento máximo y dispersión del balanceo."
                )
                interpretationEntry(
                    "Velocidad media del COP",
                    "Trayecto dividido por la duración. Parámetro muy sensible en posturografía: a mayor velocidad, mayor esfuerzo para estabilizarse."
                )
                interpretationEntry(
                    "Frecuencia media (ML / AP)",
                    "Frecuencia promedio ponderada por potencia del espectro (FFT). Frecuencias altas pueden asociarse a temblor o rigidez."
                )
                interpretationEntry(
                    "Distribución de carga (S1/S2/S3)",
                    "Promedio, máximo y mínimo de la fuerza por celda (kg). Asimetrías sostenidas revelan apoyo preferente."
                )
            }
            .padding(.vertical, 4)
        } label: {
            Text("Interpretación de los parámetros")
                .font(.subheadline)
                .bold()
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemGroupedBackground)))
    }

    private func interpretationEntry(_ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.footnote)
                .bold()
            Text(text)
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    ReportView(record: SessionRecord(
        pointCount: 100,
        durationSeconds: 30,
        swayPathLengthMm: 240,
        ellipseAreaMm2: 320,
        meanVelocityMmS: 8,
        rangeXmm: 40,
        rmsXmm: 12,
        rangeYmm: 36,
        rmsYmm: 10,
        s1Avg: 25,
        s2Avg: 26,
        s3Avg: 25,
        s1Max: 28,
        s2Max: 30,
        s3Max: 28,
        s1Min: 22,
        s2Min: 22,
        s3Min: 21,
        totalAvgLoad: 76,
        meanFreqXHz: 0.55,
        meanFreqYHz: 0.62,
        deviceName: "Plataforma FootX",
        timestamp: Date(),
        xs: [0, 5, 10, 8, 3],
        ys: [0, 2, -1, 3, 1],
        ts: [0, 100, 200, 300, 400],
        ellipse: nil
    ))
}
