package br.com.mv.cccopilotpropertie.copilot.intent.parser;

import br.com.mv.cccopilotpropertie.copilot.domain.ConversationState;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestionParser {

    private static final Pattern FIELD_PATTERN =
            Pattern.compile("campo\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern DTO_PATTERN =
            Pattern.compile("(\\w+)DTO", Pattern.CASE_INSENSITIVE);

    public void enrich(String question, ConversationState state) {

        if (state.getField() == null) {
            extractField(question).ifPresent(state::setField);
        }

        if (state.getDto() == null) {
            extractDto(question).ifPresent(state::setDto);
        }
    }

    private Optional<String> extractField(String q) {
        Matcher m = FIELD_PATTERN.matcher(q);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private Optional<String> extractDto(String q) {
        Matcher m = DTO_PATTERN.matcher(q);
        return m.find() ? Optional.of(m.group(1) + "DTO") : Optional.empty();
    }
}
