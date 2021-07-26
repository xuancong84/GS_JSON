
import java.io.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.*;
import com.google.gson.*;

/**
 * Parses and serialize Giant-String JSON, converting it into {@link ArrayList}s and {@link LinkedHashMap}s. Is thread safe.
 * Avoid escaping super-long strings, super-long strings are in the form: '123456789 1'r3e"11ew...'
 * where 123456789 is the string length and no string escaping is done for performance
 * @author Wang Xuancong, mitch
 * @since 22/07/21
 */
public class GS_JSON {
	
	private static NumberFormat nf = NumberFormat.getNumberInstance();
	
	// Benchmark: compare with standard Java JSON library
	public static void main(String[] args) throws Exception {
		long tm=0;
		Object obj;
		String json_str;
		
		// 0. Randomly generate a giant JSON string
		Random random = new Random();
		Function <Integer, String> rnd_str = (len) -> {
			byte [] array = new byte[len];
			random.nextBytes(array);
			for(int i=0; i<len; i++) array[i]&=0x7f;
			try {return new String(array, "UTF-8"); }
			catch (Exception e){ return ""; }
		};
		System.out.print("Generating huge JSON object ...");
		JsonArray json = new JsonArray();
		for(int i=0; i<10; ++i) {
			JsonObject obj1 = new JsonObject();
			for(int j=0; j<i; ++j) {
				obj1.put(random.nextInt(), rnd_str.apply(1<<i));
				obj1.put(rnd_str.apply(16), rnd_str.apply(65536<<i));
				tm += (1<<i) + 16 + (65536<<i);
			}
			json.add(obj1);
		}
		System.out.println("Done");
		System.out.println("Total length of all String objects = " + tm);
		
		// 1. Time GS_JSON library
		System.out.println("GS_JSON Benchmark:");
		System.out.print("Converting JSON object to JSON string ... ");
		tm = System.currentTimeMillis();
		json_str = toJSON(json);
		System.out.println((System.currentTimeMillis()-tm)/1000.0+"s (output length="+json_str.length()+")");
		System.out.print("Converting JSON string to JSON object ...");
		tm = System.currentTimeMillis();
		obj = GS_JSON.parse(json_str);
		System.out.println((System.currentTimeMillis()-tm)/1000.0+"s");
		
		// 2. Time standard Java JSON library
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		System.out.println("Java JSON library GSON Benchmark:");
		System.out.print("Converting JSON object to JSON string ... ");
		tm = System.currentTimeMillis();
		json_str = gson.toJson(json);
		System.out.println((System.currentTimeMillis()-tm)/1000.0+"s (output length="+json_str.length()+")");
		System.out.print("Converting JSON string to JSON object ...");
		tm = System.currentTimeMillis();
		obj = gson.fromJson(json_str, ArrayList.class);
		System.out.println((System.currentTimeMillis()-tm)/1000.0+"s");
	}
	
	
	/**Convert a string to a JSON string (including the quotes)
	 * Handles giant strings (longer than 1024) efficiently */
	public static String convert_string(String s){ return convert_string(s, 1024); }
	public static String convert_string(String s, int str_limit){
		if(s.length()>str_limit)
			return "'"+s.length()+" "+s+"'";

		StringBuilder out = new StringBuilder();
		char ch;
		for(int i=0, I=s.length(); i<I; ++i){
			switch (ch=s.charAt(i)){
				case '\n': out.append("\\n"); break;
				case '\t': out.append("\\t"); break;
				case '\r': out.append("\\r"); break;
				case '\\': out.append("\\\\"); break;
				case '"': out.append("\\\""); break;
				case '\b': out.append("\\b"); break;
				case '\f': out.append("\\f"); break;
				default:
					if(ch>=32 && ch<128)
						out.append(ch);
					else
						out.append(String.format("\\u%04x", (int)ch));
			}
		}
		return '"'+out.toString()+'"';
	}

	/** Convert a generic Object to a JSON string recursively */
	public static String toJSON(Object obj){ return toJSON(obj, 1024); }
	public static String toJSON(Object obj, int str_limit){
		if(obj instanceof String){
			return convert_string((String)obj, str_limit);
		}else if(obj instanceof JsonObject){
			StringBuilder out = new StringBuilder();
			((JsonObject) obj).forEach((k,v) -> { out.append(toJSON(k)+':'+toJSON(v)+','); });
			if(!((JsonObject) obj).isEmpty())
				out.setLength(out.length()-1);
			return '{'+out.toString()+'}';
		}else if(obj instanceof JsonArray){
			StringBuilder out = new StringBuilder();
			for(Object o : (JsonArray)obj)
				out.append(toJSON(o)+',');
			if(!((JsonArray) obj).isEmpty())
				out.setLength(out.length()-1);
			return '['+out.toString()+']';
		}else if(obj == null){
			return "null";
		}
		return obj.toString();
	}

	/** JSON Object (JSON dictionary / map) */
	public static class JsonObject <K,V> extends LinkedHashMap <K,V> {
		public int source_start, source_end;
		public String source_string;
		public JsonObject(){}
		public JsonObject(String string, int start){
			source_string = string;
			source_start = start;
		}
		public String getSourceString(){ return source_string.substring(source_start, source_end); }
		public boolean has(Object obj){return super.keySet().contains(obj);}
		public String toJSON(){ return GS_JSON.toJSON(this); }
	}

	/** JSON Array (JSON list) */
	public static class JsonArray <E> extends ArrayList <E> {
		public int source_start, source_end;
		public String source_string;
		public JsonArray(){}
		public JsonArray(String string, int start){
			source_string = string;
			source_start = start;
		}
		public String getSourceString(){ return source_string.substring(source_start, source_end); }
		public String toJSON(){ return GS_JSON.toJSON(this); }
	}

	private GS_JSON() {}

	/**
	 * Converts jsonString into a {@link Map}
	 * @param jsonString parsed
	 * @return the contents of the jsonString
	 */
	public static Map<String, Object> map(String jsonString) throws Exception {
		return (Map<String, Object>) parse(jsonString);
	}

	/**
	 * Converts jsonString into a {@link List}
	 * @param jsonString parsed
	 * @return the contents of the jsonString
	 */
	public static List<Object> list(String jsonString) throws Exception {
		return (List<Object>) parse(jsonString);
	}

	/**
	 * Pulls the internal JSON string from jsonString and returns it
	 * @param jsonString parsed
	 * @return the contents of the jsonString
	 */
	public static String string(String jsonString) throws Exception {
		return (String) parse(jsonString);
	}

	/**
	 * Converts jsonString into a {@link Number}, be it an integer or floating-point
	 * @param jsonString parsed
	 * @return the contents of the jsonString
	 */
	public static Number number(String jsonString) throws Exception {
		return (Number) parse(jsonString);
	}

	/**
	 * Converts jsonString into a boolean
	 * @param jsonString parsed
	 * @return the contents of the jsonString
	 */
	public static boolean bool(String jsonString) throws Exception {
		return (boolean) parse(jsonString);
	}

	/**
	 * Parses jsonString according to what the outermost structure is
	 * @param jsonString parsed
	 * @return the contents of jsonString
	 */
	@SuppressWarnings("ConstantConditions")
	public static Object parse(String jsonString) throws Exception{
		Stack<State> stateStack = new Stack<>();
		Type currentType;

		boolean expectingComma = false, expectingColon = false;
		int fieldStart = 0, end = jsonString.length() - 1, i = 0;
		Object propertyName = null;
		Object currentContainer = null;
		Object value;
		char current;

		try {
			while (Constants.isWhitespace((current = jsonString.charAt(i)))) i++;
		} catch (IndexOutOfBoundsException e) {
			throw new Exception("Provided JSON string did not contain a value");
		}

		if (current == '{') {
			currentType = Type.OBJECT;
			currentContainer = new JsonObject(jsonString, i);
			i++;
		} else if (current == '[') {
			currentType = Type.ARRAY;
			currentContainer = new JsonArray(jsonString, i);
			propertyName = null;
			i++;
		} else if (current == '"' || current == '\'') {
			currentType = Type.STRING;
			fieldStart = i;
		} else if (Constants.isLetter(current)) {
			// Assume parsing a constant ("null", "true", "false", etc)
			currentType = Type.CONSTANT;
			fieldStart = i;
		} else if (Constants.isNumberStart(current)) {
			currentType = Type.NUMBER;
			fieldStart = i;
		} else {
			throw new Exception("Unexpected character \"" + current + "\" at position="+i);
		}

		while (i <= end) {
			current = jsonString.charAt(i);
			switch (currentType) {
				case NAME:
					try {
						if (current == '"' || current == '\'') {
							ExtractedString extracted = extractString(jsonString, i);
							i = extracted.sourceEnd+1;
							propertyName = extracted.str;
						} else if (Constants.isLetter(current)) {
							// Assume parsing a constant ("null", "true", "false", etc)
							while (Constants.isLetter(jsonString.charAt(++i)));
							String s = jsonString.substring(fieldStart, i);
							propertyName = s.equals("null")?null:Boolean.parseBoolean(s);
						} else if (Constants.isNumberStart(current)) {	// Is a number
							while (Constants.isNumberChar(jsonString.charAt(++i)));
							propertyName = nf.parse(jsonString.substring(fieldStart, i));
						}
					} catch (Exception e) {
						throw new Exception("Error parsing dictionary key at position="+i);
					}
					currentType = Type.HEURISTIC;
					expectingColon = true;
					break;
				case STRING:
					try {
						ExtractedString extracted = extractString(jsonString, i);
						i = extracted.sourceEnd;
						value = extracted.str;
					} catch (Exception e) {
						throw new Exception("Error parsing string at position="+i);
					}

					if (currentContainer == null) {
						return value;
					} else {
						expectingComma = true;
						if (currentContainer instanceof Map) {
							((Map) currentContainer).put(propertyName, value);
							currentType = Type.OBJECT;
						} else {
							((List) currentContainer).add(value);
							currentType = Type.ARRAY;
						}
					}

					i++;
					break;
				case NUMBER: {
					while (Constants.isNumberChar(jsonString.charAt(++i)));
					value = nf.parse(jsonString.substring(fieldStart, i));

					if (currentContainer == null) {
						return value;
					} else {
						expectingComma = true;
						if (currentContainer instanceof Map) {
							((Map) currentContainer).put(propertyName, value);
							currentType = Type.OBJECT;
						} else {
							((List) currentContainer).add(value);
							currentType = Type.ARRAY;
						}
					}
					break;
				}
				case CONSTANT:
					while (Constants.isLetter(current) && i++ < end) {
						current = jsonString.charAt(i);
					}

					String valueString = jsonString.substring(fieldStart, i);
					switch (valueString) {
						case "false":
							value = false;
							break;
						case "true":
							value = true;
							break;
						case "null":
							value = null;
							break;
						default:
							if (currentContainer instanceof Map) {
								stateStack.push(new State(propertyName, currentContainer, Type.OBJECT));
							} else if (currentContainer instanceof List) {
								stateStack.push(new State(propertyName, currentContainer, Type.ARRAY));
							}
							throw new Exception("\"" + valueString
									+ "\" is not a valid constant. Missing quotes?");
					}

					if (currentContainer == null) {
						return value;
					} else {
						expectingComma = true;
						if (currentContainer instanceof Map) {
							((Map) currentContainer).put(propertyName, value);
							currentType = Type.OBJECT;
						} else {
							((List) currentContainer).add(value);
							currentType = Type.ARRAY;
						}
					}
					break;
				case HEURISTIC:
					while (Constants.isWhitespace(current) && i++ < end) {
						current = jsonString.charAt(i);
					}

					if (current != ':' && expectingColon) {
						stateStack.push(new State(propertyName, currentContainer, Type.OBJECT));
						throw new Exception("wasn't followed by a colon");
					}

					if (current == ':') {
						if (expectingColon) {
							expectingColon = false;
							i++;
						} else {
							stateStack.push(new State(propertyName, currentContainer, Type.OBJECT));
							throw new Exception("was followed by too many colons");
						}
					} else if (current == '"' || current == '\'') {
						currentType = Type.STRING;
						fieldStart = i;
					} else if (current == '{') {
						stateStack.push(new State(propertyName, currentContainer, Type.OBJECT));
						currentType = Type.OBJECT;
						currentContainer = new JsonObject(jsonString, i);
						i++;
					} else if (current == '[') {
						stateStack.push(new State(propertyName, currentContainer, Type.OBJECT));
						currentType = Type.ARRAY;
						currentContainer = new JsonArray(jsonString, i);
						i++;
					} else if (Constants.isLetter(current)) {
						// Assume parsing a constant ("null", "true", "false", etc)
						currentType = Type.CONSTANT;
						fieldStart = i;
					} else if (Constants.isNumberStart(current)) {
						// Is a number
						currentType = Type.NUMBER;
						fieldStart = i;
					} else {
						throw new Exception("unexpected character \"" + current +
								"\" instead of object value");
					}
					break;
				case OBJECT:
					while (Constants.isWhitespace(current) && i++ < end) {
						current = jsonString.charAt(i);
					}

					if (current == ',') {
						if (expectingComma) {
							expectingComma = false;
							i++;
						} else {
							stateStack.push(new State(propertyName, currentContainer, Type.OBJECT));
							throw new Exception("followed by too many commas");
						}
					} else if (current == '}') {
						((JsonObject)currentContainer).source_end = i+1;

						if (!stateStack.isEmpty()) {
							State upper = stateStack.pop();
							Object upperContainer = upper.container;
							Object parentName = upper.propertyName;
							currentType = upper.type;

							if (upperContainer instanceof Map) {
								((Map) upperContainer).put(parentName, currentContainer);
							} else {
								((List) upperContainer).add(currentContainer);
							}
							currentContainer = upperContainer;
							expectingComma = true;
							i++;
						} else
							return currentContainer;
					} else if (expectingComma) {
						stateStack.push(new State(propertyName, currentContainer, Type.OBJECT));
						throw new Exception("wasn't followed by a comma");
					} else {
						currentType = Type.NAME;
						fieldStart = i;
					}
					break;
				case ARRAY:
					while (Constants.isWhitespace(current) && i++ < end) {
						current = jsonString.charAt(i);
					}

					if (current != ',' && current != ']' && current != '}' && expectingComma) {
						stateStack.push(new State(null, currentContainer, Type.ARRAY));
						throw new Exception("wasn't preceded by a comma");
					}

					if (current == ',') {
						if (expectingComma) {
							expectingComma = false;
							i++;
						} else {
							stateStack.push(new State(null, currentContainer, Type.ARRAY));
							throw new Exception("preceded by too many commas");
						}
					} else if (current == '"' || current == '\'') {
						currentType = Type.STRING;
						fieldStart = i;
					} else if (current == '{') {
						stateStack.push(new State(null, currentContainer, Type.ARRAY));
						currentType = Type.OBJECT;
						currentContainer = new JsonObject(jsonString, i);
						i++;
					} else if (current == '[') {
						stateStack.push(new State(null, currentContainer, Type.ARRAY));
						currentType = Type.ARRAY;
						currentContainer = new JsonArray(jsonString, i);
						i++;
					} else if (current == ']') {
						((JsonArray)currentContainer).source_end = i+1;

						if (!stateStack.isEmpty()) {
							State upper = stateStack.pop();
							Object upperContainer = upper.container;
							Object parentName = upper.propertyName;
							currentType = upper.type;

							if (upperContainer instanceof Map) {
								((Map) upperContainer).put(parentName, currentContainer);
							} else {
								((List) upperContainer).add(currentContainer);
							}
							currentContainer = upperContainer;
							expectingComma = true;
							i++;
						} else {
							return currentContainer;
						}
					} else if (Constants.isLetter(current)) {
						// Assume parsing a   ("null", "true", "false", etc)
						currentType = Type.CONSTANT;
						fieldStart = i;
					} else if (Constants.isNumberStart(current)) {
						// Is a number
						currentType = Type.NUMBER;
						fieldStart = i;
					} else {
						stateStack.push(new State(propertyName, currentContainer, Type.ARRAY));
						throw new Exception("Unexpected character \"" + current + "\" instead of array value");
					}
					break;
			}
		}

		throw new Exception("Root element wasn't terminated correctly (Missing ']' or '}'?)");
	}

	private static ExtractedString extractString(String jsonString, int fieldStart) throws Exception {
		// Parse a Giant String if so
		if ( jsonString.charAt(fieldStart) == '\'' )
			try {   // giant string
				ExtractedString val = new ExtractedString();
				int i = fieldStart;
				while (Character.isDigit(jsonString.charAt(++i)));
				int strlen = Integer.parseInt(jsonString.substring(fieldStart+1, i));
				i++;
				val.sourceEnd = i+strlen;
				val.str = jsonString.substring(i, val.sourceEnd);
				assert jsonString.charAt(val.sourceEnd)=='\'';
				return val;
			} catch (Exception e){
				throw new Exception("Failed to parse Giant String");
			}

		// Parse a normal string
		StringBuilder builder = new StringBuilder();
		while (true) {
			int i = indexOfSpecial(jsonString, fieldStart);
			char c = jsonString.charAt(i);
			if (c == '"') {
				builder.append(jsonString.substring(fieldStart + 1, i));
				ExtractedString val = new ExtractedString();
				val.sourceEnd = i;
				val.str = builder.toString();
				return val;
			} else if (c == '\\') {
				builder.append(jsonString.substring(fieldStart + 1, i));

				c = jsonString.charAt(i + 1);
				switch (c) {
					case '"':
						builder.append('\"');
						break;
					case '\\':
						builder.append('\\');
						break;
					case '/':
						builder.append('/');
						break;
					case 'b':
						builder.append('\b');
						break;
					case 'f':
						builder.append('\f');
						break;
					case 'n':
						builder.append('\n');
						break;
					case 'r':
						builder.append('\r');
						break;
					case 't':
						builder.append('\t');
						break;
					case 'u':
						builder.append(Character.toChars(
								Integer.parseInt(jsonString.substring(i + 2, i + 6), 16)));
						fieldStart = i + 5; // Jump over escape sequence and code point
						continue;

				}
				fieldStart = i + 1; // Jump over escape sequence
			} else {
				throw new IndexOutOfBoundsException();
			}
		}
	}

	static class State {
		final Object propertyName;
		final Object container;
		final Type type;

		State(Object propertyName, Object container, Type type) {
			this.propertyName = propertyName;
			this.container = container;
			this.type = type;
		}
	}

	/**
	 * Returns the index of either a quotation, or a control character backslash. Skips the first element.
	 * !! Do not inline this function, the JVM <3 optimising it, and inlining it slows it down ... somehow.
	 * @param str content string to find quote or backslash
	 * @param start start index to search
	 * @return index of the first quote or backslash found at or after `start`
	 */
	private static int indexOfSpecial(String str, int start) {
		while (++start < str.length() && str.charAt(start) != '"' && str.charAt(start) != '\\');
		return start;
	}
	private enum Type {
		ARRAY,
		OBJECT,
		HEURISTIC,
		NAME,
		STRING,
		NUMBER,
		CONSTANT,
	}

	private static class ExtractedString {
		int sourceEnd;
		String str;
	}
}

class Constants {
	public static boolean isWhitespace(char c) {
		return c == ' ' || c == '\n' || c == '\t';
	}
	public static boolean isLetter(char c) {
		return c >= 'a' && c <= 'z';
	}
	public static boolean isNumberStart(char c) {
		return (c >= '0' && c <= '9') || c == '-' || c == '.';
	}
	public static boolean isNumberChar(char c) {
		return "0123456789+-=.eE".indexOf(c)>=0;
	}
}



