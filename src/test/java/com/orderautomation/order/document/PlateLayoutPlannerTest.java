package com.orderautomation.order.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderautomation.order.domain.OrderLine;
import com.orderautomation.shared.AppException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlateLayoutPlannerTest {
  private final PlateLayoutPlanner planner = new PlateLayoutPlanner();

  @Test
  void plan_whenDemandHasMixedSizesPlacesEveryPieceWithinEachPlate() {
    PlateLayoutPlanner.Plan plan =
        planner.plan(List.of(line("50x70", 2), line("9x13", 100), line("21x29,7", 7)));

    assertThat(plan.plates()).isNotEmpty();
    assertThat(plan.plates())
        .allSatisfy(
            plate -> {
              assertThat(plate.pieces()).isNotEmpty();
              for (PlateLayoutPlanner.PlacedPiece piece : plate.pieces()) {
                assertThat(piece.xMm()).isGreaterThanOrEqualTo(0);
                assertThat(piece.yMm()).isGreaterThanOrEqualTo(0);
                assertThat(piece.xMm() + piece.widthMm()).isLessThanOrEqualTo(930);
                assertThat(piece.yMm() + piece.heightMm()).isLessThanOrEqualTo(640);
              }
              for (int first = 0; first < plate.pieces().size(); first++) {
                for (int second = first + 1; second < plate.pieces().size(); second++) {
                  assertThat(overlaps(plate.pieces().get(first), plate.pieces().get(second)))
                      .isFalse();
                }
              }
              assertThat(plate.pieces())
                  .extracting(PlateLayoutPlanner.PlacedPiece::sequence)
                  .isSorted();
            });

    assertThat(
            plan.plates().stream()
                .flatMap(plate -> plate.pieces().stream())
                .filter(piece -> piece.format().equals("50x70"))
                .count())
        .isEqualTo(2);
    assertThat(
            plan.plates().stream()
                .flatMap(plate -> plate.pieces().stream())
                .filter(piece -> piece.format().equals("9x13"))
                .count())
        .isEqualTo(100);
    assertThat(
            plan.plates().stream()
                .flatMap(plate -> plate.pieces().stream())
                .filter(piece -> piece.format().equals("21x29,7"))
                .count())
        .isEqualTo(7);
    assertThat(
            plan.plates().stream()
                .flatMap(plate -> plate.pieces().stream())
                .filter(PlateLayoutPlanner.PlacedPiece::rotated)
                .count())
        .isGreaterThanOrEqualTo(2);
  }

  @Test
  void plan_whenDimensionCannotFitEvenRotatedRejectsTheOrder() {
    assertThatThrownBy(() -> planner.plan(List.of(line("100x100", 1))))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("cannot fit on a 93x64 cm plate");
  }

  private OrderLine line(String format, int quantity) {
    return new OrderLine("ITEM", null, "ZEYNA", "BLANC", format, quantity);
  }

  private boolean overlaps(
      PlateLayoutPlanner.PlacedPiece first, PlateLayoutPlanner.PlacedPiece second) {
    return first.xMm() < second.xMm() + second.widthMm()
        && first.xMm() + first.widthMm() > second.xMm()
        && first.yMm() < second.yMm() + second.heightMm()
        && first.yMm() + first.heightMm() > second.yMm();
  }
}
