package top.chiloven.lukosbot2.util.brigadier.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import top.chiloven.lukosbot2.model.message.Address;

import java.util.Arrays;
import java.util.Collection;

public final class AddressArgumentType implements ArgumentType<Address> {

    private static final Collection<String> EXAMPLES = Arrays.asList("TELEGRAM:g:-917823649784", "DISCORD:p:239546895");

    public static AddressArgumentType address() {
        return new AddressArgumentType();
    }

    public static <S> Address getAddress(CommandContext<S> ctx, String name) {
        return ctx.getArgument(name, Address.class);
    }

    @Override
    public Address parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();

        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }

        String raw = reader.getString().substring(start, reader.getCursor());

        try {
            return Address.parse(raw);
        } catch (IllegalArgumentException e) {
            reader.setCursor(start);
            throw new SimpleCommandExceptionType(e::getMessage).createWithContext(reader);
        }
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

}
