package tajo.datum;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestStringDatum {
	
	@Test
	public final void testType() {
		Datum d = DatumFactory.createString("12345");
		assertEquals(d.type(), DatumType.STRING);
	}
	
	@Test
	public final void testAsInt() {
		Datum d = DatumFactory.createString("12345");
		assertEquals(12345,d.asInt());
	}

	@Test
	public final void testAsLong() {
		Datum d = DatumFactory.createString("12345");
		assertEquals(12345l,d.asLong());
	}

	@Test
	public final void testAsFloat() {
		Datum d = DatumFactory.createString("12345");
		assertTrue(12345.0f == d.asFloat());
	}

	@Test
	public final void testAsDouble() {
		Datum d = DatumFactory.createString("12345");
		assertTrue(12345.0d == d.asDouble());
	}

	@Test
	public final void testAsChars() {
		Datum d = DatumFactory.createString("12345");
		assertEquals("12345", d.asChars());
	}
	
	@Test
  public final void testSize() {
	  Datum d = DatumFactory.createString("12345");
    assertEquals(5, d.size());
  }
}