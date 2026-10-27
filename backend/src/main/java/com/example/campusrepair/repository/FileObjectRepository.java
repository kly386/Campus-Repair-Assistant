package com.example.campusrepair.repository;

import com.example.campusrepair.entity.FileObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文件对象元数据访问仓库
 *
 * @Description FileObject JPA 数据访问层
 * @Author 开璐瑶
 * @Date 2026-10-27
 * @Version 1.0
 */
@Repository
public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    /**
     * 按工单 ID 查询附件列表（列表页场景：可容忍主从延迟，走从库）
     *
     * @param orderId 工单 ID
     * @return 附件列表
     */
    List<FileObject> findByOrderId(Long orderId);

    /**
     * 按 ID 集合批量查询（批量下载打包场景）
     *
     * @param ids 文件 ID 集合
     * @return 文件列表
     */
    List<FileObject> findByIdIn(List<Long> ids);
}
