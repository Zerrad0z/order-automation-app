package com.orderautomation.order.document;

import com.orderautomation.order.domain.OrderLine;
import com.orderautomation.shared.AppException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PlateLayoutPlanner {
  private static final int PLATE_WIDTH_MM = 930;
  private static final int PLATE_HEIGHT_MM = 640;
  private static final int MAX_PLATES = 100_000;
  private static final Pattern DIMENSION_PATTERN =
      Pattern.compile(
          "(\\d+(?:[.,]\\d+)?)\\s*[x×]\\s*(\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE);

  public Plan plan(List<OrderLine> orderLines) {
    List<RemainingDemand> demands = aggregateDemands(orderLines);
    List<Plate> plates = new ArrayList<>();
    while (demands.stream().anyMatch(demand -> demand.remaining() > 0)) {
      if (plates.size() >= MAX_PLATES) {
        throw new AppException(
            "DA_LAYOUT_TOO_LARGE",
            "The DA layout exceeds the supported limit of " + MAX_PLATES + " plates.");
      }
      PlateBuilder plate = new PlateBuilder();
      while (true) {
        PlacementCandidate candidate = findBestPlacement(plate.freeRectangles(), demands);
        if (candidate == null) {
          break;
        }
        plate.place(candidate);
        candidate.demand().decrement();
      }
      if (plate.pieces().isEmpty()) {
        throw new AppException(
            "DA_LAYOUT_NO_FIT", "An order dimension cannot fit on a 93x64 cm plate.");
      }
      plates.add(plate.toPlate(plates.size() + 1));
    }
    return new Plan(plates);
  }

  private List<RemainingDemand> aggregateDemands(List<OrderLine> lines) {
    Map<String, RemainingDemand> demands = new LinkedHashMap<>();
    for (OrderLine line : lines) {
      if (line.quantity() <= 0) {
        throw new AppException(
            "DA_LAYOUT_INVALID_QUANTITY",
            "The requested quantity for " + line.format() + " must be positive.");
      }
      Dimensions dimensions = parseDimensions(line.format());
      String key = dimensions.widthMm() + "x" + dimensions.heightMm();
      RemainingDemand existing = demands.get(key);
      if (existing == null) {
        demands.put(
            key,
            new RemainingDemand(
                demands.size(),
                line.format(),
                dimensions.widthMm(),
                dimensions.heightMm(),
                line.quantity()));
      } else {
        try {
          existing.add(line.quantity());
        } catch (ArithmeticException exception) {
          throw new AppException(
              "DA_LAYOUT_QUANTITY_TOO_LARGE",
              "The requested quantity for " + line.format() + " is too large.",
              exception);
        }
      }
    }
    if (demands.isEmpty()) {
      throw new AppException("DA_LAYOUT_EMPTY", "The order has no dimensions to lay out.");
    }
    return List.copyOf(demands.values());
  }

  private Dimensions parseDimensions(String format) {
    Matcher matcher = DIMENSION_PATTERN.matcher(format);
    if (!matcher.matches()) {
      throw new AppException(
          "DA_LAYOUT_INVALID_DIMENSION", "Unrecognized frame dimension: " + format + ".");
    }
    try {
      int width =
          new BigDecimal(matcher.group(1).replace(',', '.'))
              .multiply(BigDecimal.TEN)
              .intValueExact();
      int height =
          new BigDecimal(matcher.group(2).replace(',', '.'))
              .multiply(BigDecimal.TEN)
              .intValueExact();
      if (width <= 0 || height <= 0) {
        throw new ArithmeticException("Dimension must be positive.");
      }
      if (!fitsOnPlate(width, height)) {
        throw new AppException(
            "DA_LAYOUT_NO_FIT",
            "Dimension " + format + " cannot fit on a 93x64 cm plate, even when rotated.");
      }
      return new Dimensions(width, height);
    } catch (NumberFormatException | ArithmeticException exception) {
      throw new AppException(
          "DA_LAYOUT_INVALID_DIMENSION", "Invalid frame dimension: " + format + ".", exception);
    }
  }

  private boolean fitsOnPlate(int width, int height) {
    return (width <= PLATE_WIDTH_MM && height <= PLATE_HEIGHT_MM)
        || (height <= PLATE_WIDTH_MM && width <= PLATE_HEIGHT_MM);
  }

  private PlacementCandidate findBestPlacement(
      List<FreeRectangle> freeRectangles, List<RemainingDemand> demands) {
    PlacementCandidate best = null;
    for (FreeRectangle free : freeRectangles) {
      for (RemainingDemand demand : demands) {
        if (demand.remaining() == 0) {
          continue;
        }
        best = betterCandidate(best, candidateFor(free, demand, false));
        if (demand.widthMm() != demand.heightMm()) {
          best = betterCandidate(best, candidateFor(free, demand, true));
        }
      }
    }
    return best;
  }

  private PlacementCandidate candidateFor(
      FreeRectangle free, RemainingDemand demand, boolean rotated) {
    int width = rotated ? demand.heightMm() : demand.widthMm();
    int height = rotated ? demand.widthMm() : demand.heightMm();
    int leftoverWidth = free.widthMm() - width;
    int leftoverHeight = free.heightMm() - height;
    if (leftoverWidth < 0 || leftoverHeight < 0) {
      return null;
    }
    return new PlacementCandidate(
        demand,
        new PlacedPiece(
            demand.sequence(), demand.format(), free.xMm(), free.yMm(), width, height, rotated),
        Math.min(leftoverWidth, leftoverHeight),
        Math.max(leftoverWidth, leftoverHeight));
  }

  private PlacementCandidate betterCandidate(
      PlacementCandidate current, PlacementCandidate candidate) {
    if (candidate == null) {
      return current;
    }
    if (current == null) {
      return candidate;
    }
    return PlacementCandidate.ORDER.compare(candidate, current) < 0 ? candidate : current;
  }

  private record Dimensions(int widthMm, int heightMm) {}

  private static final class RemainingDemand {
    private final int sequence;
    private final String format;
    private final int widthMm;
    private final int heightMm;
    private int remaining;

    private RemainingDemand(int sequence, String format, int widthMm, int heightMm, int remaining) {
      this.sequence = sequence;
      this.format = format;
      this.widthMm = widthMm;
      this.heightMm = heightMm;
      this.remaining = remaining;
    }

    private int sequence() {
      return sequence;
    }

    private String format() {
      return format;
    }

    private int widthMm() {
      return widthMm;
    }

    private int heightMm() {
      return heightMm;
    }

    private int remaining() {
      return remaining;
    }

    private void add(int quantity) {
      remaining = Math.addExact(remaining, quantity);
    }

    private void decrement() {
      remaining--;
    }
  }

  private record FreeRectangle(int xMm, int yMm, int widthMm, int heightMm) {}

  private record PlacementCandidate(
      RemainingDemand demand, PlacedPiece piece, int shortSideFit, int longSideFit) {
    private static final Comparator<PlacementCandidate> ORDER =
        Comparator.comparingInt(PlacementCandidate::shortSideFit)
            .thenComparingInt(PlacementCandidate::longSideFit)
            .thenComparingInt(candidate -> candidate.demand().sequence());
  }

  private static final class PlateBuilder {
    private final List<FreeRectangle> freeRectangles =
        new ArrayList<>(List.of(new FreeRectangle(0, 0, PLATE_WIDTH_MM, PLATE_HEIGHT_MM)));
    private final List<PlacedPiece> pieces = new ArrayList<>();

    private List<FreeRectangle> freeRectangles() {
      return freeRectangles;
    }

    private List<PlacedPiece> pieces() {
      return pieces;
    }

    private void place(PlacementCandidate candidate) {
      PlacedPiece piece = candidate.piece();
      pieces.add(piece);
      List<FreeRectangle> split = new ArrayList<>();
      for (FreeRectangle free : freeRectangles) {
        splitFreeRectangle(free, piece, split);
      }
      pruneContainedRectangles(split);
      freeRectangles.clear();
      freeRectangles.addAll(split);
    }

    private void splitFreeRectangle(
        FreeRectangle free, PlacedPiece piece, List<FreeRectangle> split) {
      int freeRight = free.xMm() + free.widthMm();
      int freeBottom = free.yMm() + free.heightMm();
      int pieceRight = piece.xMm() + piece.widthMm();
      int pieceBottom = piece.yMm() + piece.heightMm();
      boolean intersects =
          piece.xMm() < freeRight
              && pieceRight > free.xMm()
              && piece.yMm() < freeBottom
              && pieceBottom > free.yMm();
      if (!intersects) {
        split.add(free);
        return;
      }
      if (piece.xMm() < freeRight && pieceRight > free.xMm()) {
        addRectangle(split, free.xMm(), free.yMm(), free.widthMm(), piece.yMm() - free.yMm());
        addRectangle(split, free.xMm(), pieceBottom, free.widthMm(), freeBottom - pieceBottom);
      }
      if (piece.yMm() < freeBottom && pieceBottom > free.yMm()) {
        addRectangle(split, free.xMm(), free.yMm(), piece.xMm() - free.xMm(), free.heightMm());
        addRectangle(split, pieceRight, free.yMm(), freeRight - pieceRight, free.heightMm());
      }
    }

    private void addRectangle(List<FreeRectangle> rectangles, int x, int y, int width, int height) {
      if (width > 0 && height > 0) {
        rectangles.add(new FreeRectangle(x, y, width, height));
      }
    }

    private void pruneContainedRectangles(List<FreeRectangle> rectangles) {
      for (int first = 0; first < rectangles.size(); first++) {
        FreeRectangle firstRectangle = rectangles.get(first);
        for (int second = first + 1; second < rectangles.size(); second++) {
          FreeRectangle secondRectangle = rectangles.get(second);
          if (contains(firstRectangle, secondRectangle)) {
            rectangles.remove(second--);
          } else if (contains(secondRectangle, firstRectangle)) {
            rectangles.remove(first--);
            break;
          }
        }
      }
    }

    private boolean contains(FreeRectangle outer, FreeRectangle inner) {
      return inner.xMm() >= outer.xMm()
          && inner.yMm() >= outer.yMm()
          && inner.xMm() + inner.widthMm() <= outer.xMm() + outer.widthMm()
          && inner.yMm() + inner.heightMm() <= outer.yMm() + outer.heightMm();
    }

    private Plate toPlate(int number) {
      List<PlacedPiece> orderedPieces =
          pieces.stream().sorted(Comparator.comparingInt(PlacedPiece::sequence)).toList();
      return new Plate(number, orderedPieces);
    }
  }

  public record Plan(List<Plate> plates) {
    public Plan {
      plates = List.copyOf(plates);
    }
  }

  public record Plate(int number, List<PlacedPiece> pieces) {
    public Plate {
      pieces = List.copyOf(pieces);
    }
  }

  public record PlacedPiece(
      int sequence, String format, int xMm, int yMm, int widthMm, int heightMm, boolean rotated) {}
}
