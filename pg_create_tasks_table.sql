-- PostgreSQL版本 - 创建新的 tasks 表，将任务数据结构化存储
-- 适用于 Supabase (PostgreSQL)

-- 创建任务表
CREATE TABLE IF NOT EXISTS tasks (
    id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    notes TEXT,
    assignee VARCHAR(100),
    category VARCHAR(50) DEFAULT 'work',
    deadline TIMESTAMP WITH TIME ZONE,
    priority VARCHAR(20) DEFAULT 'medium' CHECK (priority IN ('low', 'medium', 'high')),
    assignees JSONB, -- PostgreSQL使用JSONB而不是JSON，性能更好
    completed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    completed_by VARCHAR(100),
    completion_notes TEXT,
    completion_image TEXT, -- PostgreSQL中使用TEXT代替LONGTEXT
    completion_images JSONB, -- 存储多张完成图片的数组
    user_id VARCHAR(100),
    data_path VARCHAR(255) -- 原数据路径，用于追溯来源
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_tasks_user_id ON tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_category ON tasks(category);
CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority);
CREATE INDEX IF NOT EXISTS idx_tasks_completed ON tasks(completed);
CREATE INDEX IF NOT EXISTS idx_tasks_deadline ON tasks(deadline);
CREATE INDEX IF NOT EXISTS idx_tasks_created_at ON tasks(created_at);
CREATE INDEX IF NOT EXISTS idx_tasks_assignee ON tasks(assignee);
CREATE INDEX IF NOT EXISTS idx_tasks_title ON tasks USING gin(to_tsvector('english', title)); -- 全文搜索索引

-- 为了保持向后兼容，检查并添加迁移标记列到原表
DO $$
BEGIN
    -- 检查列是否存在，如果不存在则添加
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'daily_todo'
        AND column_name = 'migrated'
        AND table_schema = current_schema()
    ) THEN
        ALTER TABLE daily_todo ADD COLUMN migrated BOOLEAN DEFAULT FALSE;
    END IF;
END $$;

-- 创建用于生成随机ID的函数
CREATE OR REPLACE FUNCTION generate_task_id()
RETURNS VARCHAR(50) AS $$
BEGIN
    RETURN lower(encode(gen_random_bytes(12), 'hex'));
END;
$$ LANGUAGE plpgsql;

-- 创建触发器函数用于更新时间戳
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 添加updated_at字段（如果需要）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tasks'
        AND column_name = 'updated_at'
        AND table_schema = current_schema()
    ) THEN
        ALTER TABLE tasks ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();

        -- 创建更新触发器
        CREATE TRIGGER update_tasks_updated_at
            BEFORE UPDATE ON tasks
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- 添加注释
COMMENT ON TABLE tasks IS '结构化的任务表，从daily_todo.tasks JSON字段迁移而来';
COMMENT ON COLUMN tasks.id IS '任务唯一标识符';
COMMENT ON COLUMN tasks.title IS '任务标题';
COMMENT ON COLUMN tasks.notes IS '任务备注';
COMMENT ON COLUMN tasks.assignee IS '主要负责人';
COMMENT ON COLUMN tasks.category IS '任务分类';
COMMENT ON COLUMN tasks.deadline IS '截止时间';
COMMENT ON COLUMN tasks.priority IS '优先级: low, medium, high';
COMMENT ON COLUMN tasks.assignees IS '所有负责人的JSON数组';
COMMENT ON COLUMN tasks.completed IS '是否已完成';
COMMENT ON COLUMN tasks.created_at IS '创建时间';
COMMENT ON COLUMN tasks.completed_at IS '完成时间';
COMMENT ON COLUMN tasks.completed_by IS '完成人';
COMMENT ON COLUMN tasks.completion_notes IS '完成备注';
COMMENT ON COLUMN tasks.completion_image IS '完成时的图片数据';
COMMENT ON COLUMN tasks.completion_images IS '完成时的多张图片数据';
COMMENT ON COLUMN tasks.user_id IS '所属用户ID';
COMMENT ON COLUMN tasks.data_path IS '数据来源路径，用于迁移追溯';