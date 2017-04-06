package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.fp.Function;
import water.udf.specialized.EnumColumn;
import water.udf.specialized.Enums;

import java.io.IOException;

import static water.udf.DataColumns.BaseFactory;

/**
 * Single column frame that knows its data type and can materialize
 */
public class SingleColumnFrame<DataType, ColumnType extends DataColumn<DataType>>
    extends Frame {
  private final ColumnFactory<DataType, ColumnType> factory;
  private final long length;
  private final Function<Long, DataType> function;
  private Column<DataType> column;

  /**
   * deserialization :(
   */
  public SingleColumnFrame() {
    factory = null;
    length = -1;
    function = null;
  }

  SingleColumnFrame(BaseFactory<DataType, ColumnType> factory, long length, Function<Long, DataType> function) {
    super();
    this.factory = factory;
    this.length = length;
    this.function = function;
  }

  public static <DataType, ColumnType extends DataColumn<DataType>>
    SingleColumnFrame<DataType, ColumnType> forColumn(
      final BaseFactory<DataType, ColumnType> factory,
      final Column<DataType> column) {
    return new SingleColumnFrame<DataType, ColumnType>(factory, column.size(), column) {
      @Override
      protected Vec buildZeroVec() { return factory.buildZeroVec(column); }
    };
  }

  public final static class EnumFrame extends SingleColumnFrame<Integer, EnumColumn> {
    private final String[] domain;

    public EnumFrame(long length, Function<Long, Integer> function, String[] domain) {
      super(Enums.enums(domain), length, function);
      this.domain = domain;
    }
  }

  protected Vec buildZeroVec() {
    return factory.buildZeroVec(length);
  }

  protected Vec makeVec() throws IOException {
    final Vec vec0 = buildZeroVec();
    MRTask task = new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (Chunk c : cs) {
          DataChunk<DataType> tc = factory.apply(c);
          for (int r = 0; r < c._len; r++) {
            long i = r + c.start();
            tc.set(r, function.apply(i));
          }
        }
      }
    };
    MRTask mrTask = task.doAll(vec0);
    return mrTask._fr.vecs()[0];
  }

  protected ColumnType newColumn(Vec vec) throws IOException {
    return factory.newColumn(vec);
  }

  public ColumnType newColumn() throws IOException {
    return newColumn(makeVec());
  }

}