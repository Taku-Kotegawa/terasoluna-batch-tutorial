package org.terasoluna.batch.job02;

import com.github.dozermapper.core.Mapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.validator.ValidationException;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.SmartValidator;

import java.time.LocalDateTime;

/**
 * job02ジョブ実装
 * CSV -> VARIABLEテーブル
 */
@Slf4j
@Component
public class Job02Tasklet implements Tasklet {

    @Autowired
    VariableRepository variableRepository;

    @Autowired
    ItemStreamReader<VariableCsv> csvReader;

    @Autowired
    SmartValidator smartValidator;

    @Autowired
    Mapper beanMapper;

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {

        // DB操作時の例外発生の有無を記録する
        boolean hasDBAccessException = false;

        // CSVファイルを１行を格納するmodel
        VariableCsv csvLine = null;

        // CSVファイルの入力チェック結果を格納するBindingResult
        BindingResult result = new BeanPropertyBindingResult(csvLine, "csvLine");

        log.info("Start");

        try {
            /*
             * 入力チェック(全件チェック)
             */
            csvReader.open(chunkContext.getStepContext().getStepExecution().getExecutionContext());
            while ((csvLine = csvReader.read()) != null) {
                smartValidator.validate(csvLine, result);
            }
            if (result.hasErrors()) {
                result.getAllErrors().forEach(r -> log.error(r.toString()));
                csvReader.close();
                throw new ValidationException("入力チェックでエラーを検出したため、処理を中断。");
            }
            csvReader.close();

            /*
             * テーブル更新(一括コミット)
             */
            csvReader.open(chunkContext.getStepContext().getStepExecution().getExecutionContext());
            while ((csvLine = csvReader.read()) != null) {
                Variable v = map(csvLine);
                try {
                    Variable current = variableRepository.selectByPrimaryKey(v.getId());
                    if (current == null) {
                        v.setVersion(0L);
                        variableRepository.insert(v);
                    } else {
                        if (!v.equals(current)) {
                            v.setVersion(current.getVersion() + 1L);    // リポジトリ側で対処している場合は不要
                            v.setCreatedBy(current.getCreatedBy());     // リポジトリ側で対処している場合は不要
                            v.setCreatedDate(current.getCreatedDate()); // リポジトリ側で対処している場合は不要
                            variableRepository.updateByPrimaryKeyWithBLOBs(v);
                        }
                    }
                } catch (Exception e) {
                    log.error("Exception: " + e.getLocalizedMessage());
                    hasDBAccessException = true;
                }
            }

            if (hasDBAccessException) {
                throw new Exception("データベース更新時にエラーが発生したため、処理を中断。");
            }

        } finally {
            csvReader.close();
        }

        log.info("End");
        return RepeatStatus.FINISHED;
    }

    /**
     * CSV->Variable変換
     *
     * @param csv CSV一行を表すModel
     * @return VariableのModel
     */
    private Variable map(VariableCsv csv) {
        String JOB_EXECUTOR = "job_user";

        Variable v = beanMapper.map(csv, Variable.class);
        v.setCreatedDate(LocalDateTime.now());
        v.setLastModifiedDate(LocalDateTime.now());
        v.setCreatedBy(JOB_EXECUTOR);
        v.setLastModifiedBy(JOB_EXECUTOR);
        return v;
    }
}
