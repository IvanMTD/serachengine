package searchengine.dto.messages;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class DataResponse {
    private boolean result;
    private long count;
    private String error;
    private List<DataDTO> dataDTOList;
}
