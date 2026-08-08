-- =============================================
-- 100w 订单数据造数脚本
-- 用法：mysql -u root -p123456 logistics_db < generate-orders-data.sql
-- 预计耗时：2~5 分钟（机械硬盘），SSD 更快
-- =============================================

DELIMITER $$

DROP PROCEDURE IF EXISTS generate_orders$$

CREATE PROCEDURE generate_orders(IN total_rows INT)
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_size INT DEFAULT 1000;
    DECLARE batch_start INT;
    DECLARE random_day INT;
    DECLARE random_status VARCHAR(20);
    DECLARE random_courier_id BIGINT;

    -- 姓、名池子，随机拼出客户名
    DECLARE surnames VARCHAR(200) DEFAULT '张,李,王,刘,陈,杨,赵,黄,周,吴,徐,孙,胡,朱,高,林,何,郭,马,罗';
    DECLARE given_names VARCHAR(200) DEFAULT '伟,芳,娜,敏,静,丽,强,磊,军,洋,勇,艳,杰,涛,明,超,秀兰,华,兵,刚';

    -- 地址池
    DECLARE roads VARCHAR(500) DEFAULT '中山路,解放路,人民路,建设路,和平路,文化路,长安街,南京路,北京路,朝阳路';

    -- 清空表（保留 courier 表不动）
    TRUNCATE TABLE orders;

    -- 关掉自动提交和约束检查，批量插入飞快
    SET autocommit = 0;
    SET FOREIGN_KEY_CHECKS = 0;

    WHILE i <= total_rows DO
        SET batch_start = i;
        SET i = i + batch_size;

        -- 批量插入 1000 条
        SET @sql = CONCAT(
            'INSERT INTO orders (customer_name, address, phone, status, courier_id, created_at) VALUES ',
            (SELECT GROUP_CONCAT(
                CONCAT('(',
                    '"',  ELT(1 + FLOOR(RAND() * 20), '张','李','王','刘','陈','杨','赵','黄','周','吴','徐','孙','胡','朱','高','林','何','郭','马','罗'),
                          ELT(1 + FLOOR(RAND() * 20), '伟','芳','娜','敏','静','丽','强','磊','军','洋','勇','艳','杰','涛','明','超','秀兰','华','兵','刚'), '", ',
                    '"',  ELT(1 + FLOOR(RAND() * 10), '中山路','解放路','人民路','建设路','和平路','文化路','长安街','南京路','北京路','朝阳路'),
                          FLOOR(1 + RAND() * 500), '号", ',
                    '"1',  ELT(1 + FLOOR(RAND() * 5), '38','39','58','86','37'),
                          FLOOR(10000000 + RAND() * 90000000), '", ',
                    '"',  ELT(1 + FLOOR(RAND() * 5), 'CREATED','ASSIGNED','DELIVERING','COMPLETED','CANCELLED'), '", ',
                    FLOOR(1 + RAND() * 1000), ', ',
                    'DATE_SUB(NOW(), INTERVAL ', FLOOR(RAND() * 365), ' DAY)',
                ')'
                ) SEPARATOR ','
            )
        );

        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        IF batch_start % 10000 = 1 THEN
            COMMIT;
            SELECT CONCAT('已插入 ', batch_start, ' 条...') AS progress;
        END IF;
    END WHILE;

    COMMIT;
    SET FOREIGN_KEY_CHECKS = 1;
    SET autocommit = 1;

    SELECT CONCAT('✅ 完成！共插入 ', total_rows, ' 条订单') AS result;
END$$

DELIMITER ;

-- ===== 先造 1000 个快递员（如果 courier 表是空的） =====
-- 已有的跳过，没有的用这行：
-- INSERT IGNORE INTO courier (id, name, phone, status) ...

-- 执行造数（1000000 = 100w，想少点改这个数字）
CALL generate_orders(1000000);
