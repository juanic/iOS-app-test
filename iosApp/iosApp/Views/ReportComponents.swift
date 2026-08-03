import SwiftUI

struct MetricCard: View {
    let title: String
    let value: String
    let unit: String

    var body: some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.title3)
                .bold()
                .monospacedDigit()
            Text(unit)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(minWidth: 70)
    }
}

/// Statokinesigrama: trayectoria del COP + elipse de confianza 95% + punto medio.
struct StatogramCanvas: View {
    let xs: [Double]
    let ys: [Double]
    let ellipse: CopEllipseRecord?

    var body: some View {
        Canvas { context, size in
            guard xs.count > 1, ys.count == xs.count else { return }
            let w = Double(size.width)
            let h = Double(size.height)
            let minX = xs.min()!
            let maxX = xs.max()!
            let minY = ys.min()!
            let maxY = ys.max()!
            let spanX = max(maxX - minX, 0.001)
            let spanY = max(maxY - minY, 0.001)
            let scale = min(w / spanX, h / spanY) * 0.85
            let cx = w / 2
            let cy = h / 2
            let midX = (minX + maxX) / 2
            let midY = (minY + maxY) / 2

            func mapX(_ x: Double) -> CGFloat { CGFloat(cx + (x - midX) * scale) }
            func mapY(_ y: Double) -> CGFloat { CGFloat(cy - (y - midY) * scale) }

            let step: Double = 50
            var g = minX - minX.truncatingRemainder(dividingBy: step)
            while g <= maxX {
                var line = Path()
                line.move(to: CGPoint(x: mapX(g), y: 0))
                line.addLine(to: CGPoint(x: mapX(g), y: size.height))
                context.stroke(line, with: .color(.gray.opacity(0.2)), lineWidth: 1)
                g += step
            }
            g = minY - minY.truncatingRemainder(dividingBy: step)
            while g <= maxY {
                var line = Path()
                line.move(to: CGPoint(x: 0, y: mapY(g)))
                line.addLine(to: CGPoint(x: size.width, y: mapY(g)))
                context.stroke(line, with: .color(.gray.opacity(0.2)), lineWidth: 1)
                g += step
            }

            var axisX = Path()
            axisX.move(to: CGPoint(x: 0, y: CGFloat(cy)))
            axisX.addLine(to: CGPoint(x: size.width, y: CGFloat(cy)))
            context.stroke(axisX, with: .color(.gray.opacity(0.5)), lineWidth: 1.2)
            var axisY = Path()
            axisY.move(to: CGPoint(x: CGFloat(cx), y: 0))
            axisY.addLine(to: CGPoint(x: CGFloat(cx), y: size.height))
            context.stroke(axisY, with: .color(.gray.opacity(0.5)), lineWidth: 1.2)

            if let e = ellipse {
                let aPx = CGFloat(e.semiMajor * scale)
                let bPx = CGFloat(e.semiMinor * scale)
                let center = CGPoint(x: mapX(e.centerX), y: mapY(e.centerY))
                var ellipsePath = Path(
                    ellipseIn: CGRect(
                        x: center.x - aPx, y: center.y - bPx,
                        width: aPx * 2, height: bPx * 2
                    )
                )
                let transform = CGAffineTransform(translationX: center.x, y: center.y)
                    .rotated(by: -CGFloat(e.angleRad))
                    .translatedBy(x: -center.x, y: -center.y)
                ellipsePath = ellipsePath.applying(transform)
                context.fill(ellipsePath, with: .color(.orange.opacity(0.15)))
                context.stroke(ellipsePath, with: .color(.orange), lineWidth: 1.5)
            }

            var path = Path()
            for (i, x) in xs.enumerated() {
                let p = CGPoint(x: mapX(x), y: mapY(ys[i]))
                if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
            context.stroke(path, with: .color(.orange.opacity(0.25)), lineWidth: 6)
            context.stroke(path, with: .color(.orange), lineWidth: 2)

            let meanX = xs.reduce(0, +) / Double(xs.count)
            let meanY = ys.reduce(0, +) / Double(ys.count)
            let m = CGPoint(x: mapX(meanX), y: mapY(meanY))
            context.fill(
                Path(ellipseIn: CGRect(x: m.x - 5, y: m.y - 5, width: 10, height: 10)),
                with: .color(.green)
            )
            context.stroke(
                Path(ellipseIn: CGRect(x: m.x - 9, y: m.y - 9, width: 18, height: 18)),
                with: .color(.green),
                lineWidth: 1.5
            )
        }
    }
}

/// Serie temporal de un eje del COP.
struct TemporalSeriesCanvas: View {
    let values: [Double]
    let timestamps: [Int64]
    let color: Color

    var body: some View {
        Canvas { context, size in
            guard values.count > 1,
                  timestamps.count == values.count,
                  let t0 = timestamps.first,
                  let t1 = timestamps.last else { return }
            let w = Double(size.width)
            let h = Double(size.height)
            let duration = max(Double(t1 - t0), 1)
            let vMin = values.min()!
            let vMax = values.max()!
            let span = max(vMax - vMin, 0.001)
            let pad = span * 0.1
            let lo = vMin - pad
            let hi = vMax + pad

            func tx(_ t: Int64) -> CGFloat { CGFloat(Double(t - t0) / duration * w) }
            func vy(_ v: Double) -> CGFloat { CGFloat(h - (v - lo) / (hi - lo) * h) }

            var mid = Path()
            mid.move(to: CGPoint(x: 0, y: CGFloat(h / 2)))
            mid.addLine(to: CGPoint(x: size.width, y: CGFloat(h / 2)))
            context.stroke(mid, with: .color(.gray.opacity(0.3)), lineWidth: 1)

            var path = Path()
            for (i, v) in values.enumerated() {
                let p = CGPoint(x: tx(timestamps[i]), y: vy(v))
                if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
            context.stroke(path, with: .color(color), lineWidth: 1.6)
        }
    }
}
