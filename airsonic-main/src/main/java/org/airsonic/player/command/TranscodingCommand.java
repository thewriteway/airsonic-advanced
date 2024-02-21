package org.airsonic.player.command;

import org.airsonic.player.domain.Transcoding;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TranscodingCommand {

    private TranscodingDTO newTranscoding = new TranscodingDTO();
    private List<TranscodingDTO> transcodings = new ArrayList<>();
    private Path transcodeDirectory;
    private String splitOptions;
    private String splitCommand;
    private String downsampleCommand;
    private String hlsCommand;
    private String jukeboxCommand;
    private String videoImageCommand;
    private String subtitlesExtractionCommand;
    private Long transcodeEstimateTimePadding;
    private Long transcodeEstimateBytePadding;
    private String brand;

    public TranscodingDTO getNewTranscoding() {
        return newTranscoding;
    }

    public void setNewTranscoding(TranscodingDTO newTranscoding) {
        this.newTranscoding = newTranscoding;
    }

    public List<TranscodingDTO> getTranscodings() {
        return transcodings;
    }

    public void setTranscodings(List<TranscodingDTO> transcodings) {
        this.transcodings = transcodings;
    }

    public Path getTranscodeDirectory() {
        return transcodeDirectory;
    }

    public void setTranscodeDirectory(Path transcodeDirectory) {
        this.transcodeDirectory = transcodeDirectory;
    }

    public String getSplitOptions() {
        return splitOptions;
    }

    public void setSplitOptions(String splitOptions) {
        this.splitOptions = StringUtils.trimToNull(splitOptions);
    }

    public String getSplitCommand() {
        return splitCommand;
    }

    public void setSplitCommand(String splitCommand) {
        this.splitCommand = StringUtils.trimToNull(splitCommand);
    }

    public String getDownsampleCommand() {
        return downsampleCommand;
    }

    public void setDownsampleCommand(String downsampleCommand) {
        this.downsampleCommand = StringUtils.trimToNull(downsampleCommand);
    }

    public String getHlsCommand() {
        return hlsCommand;
    }

    public void setHlsCommand(String hlsCommand) {
        this.hlsCommand = StringUtils.trimToNull(hlsCommand);
    }

    public String getJukeboxCommand() {
        return jukeboxCommand;
    }

    public void setJukeboxCommand(String jukeboxCommand) {
        this.jukeboxCommand = StringUtils.trimToNull(jukeboxCommand);
    }

    public String getVideoImageCommand() {
        return videoImageCommand;
    }

    public void setVideoImageCommand(String videoImageCommand) {
        this.videoImageCommand = StringUtils.trimToNull(videoImageCommand);
    }

    public String getSubtitlesExtractionCommand() {
        return subtitlesExtractionCommand;
    }

    public void setSubtitlesExtractionCommand(String subtitlesExtractionCommand) {
        this.subtitlesExtractionCommand = StringUtils.trimToNull(subtitlesExtractionCommand);
    }

    public Long getTranscodeEstimateTimePadding() {
        return transcodeEstimateTimePadding;
    }

    public void setTranscodeEstimateTimePadding(Long transcodeEstimateTimePadding) {
        this.transcodeEstimateTimePadding = transcodeEstimateTimePadding;
    }

    public Long getTranscodeEstimateBytePadding() {
        return transcodeEstimateBytePadding;
    }

    public void setTranscodeEstimateBytePadding(Long transcodeEstimateBytePadding) {
        this.transcodeEstimateBytePadding = transcodeEstimateBytePadding;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public static class TranscodingDTO {
        private Integer id;
        private String name;
        private String sourceFormats;
        private String targetFormat;
        private String step1;
        private String step2;
        private boolean defaultActive = true;
        private boolean delete;

        public TranscodingDTO() {
        }

        public TranscodingDTO(Transcoding transcoding) {
            this.id = transcoding.getId();
            this.name = transcoding.getName();
            this.sourceFormats = transcoding.getSourceFormats();
            this.targetFormat = transcoding.getTargetFormat();
            this.step1 = transcoding.getStep1();
            this.step2 = transcoding.getStep2();
            this.defaultActive = transcoding.isDefaultActive();
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = StringUtils.trimToNull(name);
        }

        public String getSourceFormats() {
            return sourceFormats;
        }

        public void setSourceFormats(String sourceFormats) {
            this.sourceFormats = StringUtils.trimToNull(sourceFormats);
        }

        public String getTargetFormat() {
            return targetFormat;
        }

        public void setTargetFormat(String targetFormat) {
            this.targetFormat = StringUtils.trimToNull(targetFormat);
        }

        public String getStep1() {
            return step1;
        }

        public void setStep1(String step1) {
            this.step1 = StringUtils.trimToNull(step1);
        }

        public String getStep2() {
            return step2;
        }

        public void setStep2(String step2) {
            this.step2 = StringUtils.trimToNull(step2);
        }

        public boolean isDefaultActive() {
            return defaultActive;
        }

        public void setDefaultActive(boolean defaultActive) {
            this.defaultActive = defaultActive;
        }

        public boolean isDelete() {
            return delete;
        }

        public void setDelete(boolean delete) {
            this.delete = delete;
        }

        public boolean isConfigured() {
            return name != null || sourceFormats != null || targetFormat != null || step1 != null || step2 != null;
        }
    }

}