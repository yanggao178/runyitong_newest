from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy.orm import Session
from typing import List, Optional
import logging
from database import get_db
from models import Video as VideoModel
from schemas import VideoSchema, VideoCreate, VideoUpdate, BaseResponse

# 初始化logger
logger = logging.getLogger(__name__)

router = APIRouter()

# 获取视频列表（支持分页、搜索和分类过滤）
@router.get("/")
async def get_videos(
    request: Request,
    skip: int = Query(0, ge=0, description="跳过数量"),
    limit: int = Query(10, ge=1, le=100, description="限制数量"),
    search: Optional[str] = Query(None, description="搜索关键词"),
    category: Optional[str] = Query(None, description="视频分类"),
    db: Session = Depends(get_db)
):
    """获取视频列表"""
    try:
        query = db.query(VideoModel)
        
        # 搜索过滤
        if search:
            query = query.filter(VideoModel.title.contains(search) | VideoModel.description.contains(search))
        
        # 分类过滤
        if category:
            query = query.filter(VideoModel.category == category)
        
        # 计算总数
        total = query.count()
        
        # 分页，按创建时间倒序排列
        videos = query.order_by(VideoModel.created_at.desc()).offset(skip).limit(limit).all()
        
        # 构造响应数据
        items = []
        for video in videos:
            item = {
                "id": video.id,
                "title": video.title,
                "url": video.url,
                "cover_image": video.cover_image or "",
                "duration": video.duration or 0,
                "description": video.description or "",
                "tags": video.tags or "",
                "category": video.category or "",
                "upload_time": video.upload_time or "",
                "view_count": video.view_count or 0,
                "created_at": video.created_at.isoformat() if video.created_at else None,
                "updated_at": video.updated_at.isoformat() if video.updated_at else None
            }
            items.append(item)
        
        # 直接返回视频列表，与Java端ApiService.getVideos()匹配
        return {
            "success": True,
            "message": "获取视频列表成功",
            "data": items
        }
    except Exception as e:
        logger.error(f"获取视频列表失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"服务器内部错误: {str(e)}")

# 根据分类获取视频列表（单独的分类路由，方便前端调用）
@router.get("/category/{category_name}")
async def get_videos_by_category(
    request: Request,
    category_name: str,
    skip: int = Query(0, ge=0, description="跳过数量"),
    limit: int = Query(10, ge=1, le=100, description="限制数量"),
    db: Session = Depends(get_db)
):
    """根据分类获取视频列表"""
    try:
        # 查询指定分类的视频
        query = db.query(VideoModel).filter(VideoModel.category == category_name)
        
        # 计算总数
        total = query.count()
        
        # 分页，按创建时间倒序排列
        videos = query.order_by(VideoModel.created_at.desc()).offset(skip).limit(limit).all()
        
        # 构造响应数据
        items = []
        for video in videos:
            item = {
                "id": video.id,
                "title": video.title,
                "url": video.url,
                "cover_image": video.cover_image or "",
                "duration": video.duration or 0,
                "description": video.description or "",
                "tags": video.tags or "",
                "category": video.category or "",
                "upload_time": video.upload_time or "",
                "view_count": video.view_count or 0,
                "created_at": video.created_at.isoformat() if video.created_at else None,
                "updated_at": video.updated_at.isoformat() if video.updated_at else None
            }
            items.append(item)
        
        # 构造列表响应数据
        video_list_data = {
            "items": items,
            "total": total,
            "skip": skip,
            "limit": limit
        }
        
        return {
            "success": True,
            "message": f"获取{category_name}分类的视频列表成功",
            "data": video_list_data
        }
    except Exception as e:
        logger.error(f"根据分类获取视频列表失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"服务器内部错误: {str(e)}")

# 获取单个视频详情
@router.get("/{video_id}")
async def get_video(
    video_id: int,
    request: Request,
    db: Session = Depends(get_db)
):
    """获取视频详情"""
    try:
        video = db.query(VideoModel).filter(VideoModel.id == video_id).first()
        if not video:
            raise HTTPException(status_code=404, detail="视频不存在")
        
        # 构造视频详情数据
        video_data = {
            "id": video.id,
            "title": video.title,
            "url": video.url,
            "cover_image": video.cover_image or "",
            "duration": video.duration or 0,
            "description": video.description or "",
            "tags": video.tags or "",
            "category": video.category or "",
            "upload_time": video.upload_time or "",
            "view_count": video.view_count or 0,
            "created_at": video.created_at.isoformat() if video.created_at else None,
            "updated_at": video.updated_at.isoformat() if video.updated_at else None
        }
        
        return {
            "success": True,
            "message": "获取视频详情成功",
            "data": video_data
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"获取视频详情失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"服务器内部错误: {str(e)}")

# 增加视频观看次数
@router.post("/{video_id}/increase-view")
async def increase_video_view(
    video_id: int,
    db: Session = Depends(get_db)
):
    """增加视频观看次数"""
    try:
        video = db.query(VideoModel).filter(VideoModel.id == video_id).first()
        if not video:
            raise HTTPException(status_code=404, detail="视频不存在")
        
        # 增加观看次数
        video.view_count = (video.view_count or 0) + 1
        db.commit()
        db.refresh(video)
        
        return {
            "success": True,
            "message": "观看次数增加成功",
            "data": {
                "video_id": video_id,
                "view_count": video.view_count
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        db.rollback()
        logger.error(f"增加视频观看次数失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"服务器内部错误: {str(e)}")

# 获取热门视频列表（按观看次数排序）
@router.get("/featured/hot")
async def get_hot_videos(
    request: Request,
    limit: int = Query(10, ge=1, le=50, description="限制数量"),
    db: Session = Depends(get_db)
):
    """获取热门视频列表（按观看次数排序）"""
    try:
        # 查询视频，按观看次数倒序排列
        videos = db.query(VideoModel).order_by(VideoModel.view_count.desc()).limit(limit).all()
        
        # 构造响应数据
        items = []
        for video in videos:
            item = {
                "id": video.id,
                "title": video.title,
                "url": video.url,
                "cover_image": video.cover_image or "",
                "duration": video.duration or 0,
                "description": video.description or "",
                "tags": video.tags or "",
                "category": video.category or "",
                "upload_time": video.upload_time or "",
                "view_count": video.view_count or 0,
                "created_at": video.created_at.isoformat() if video.created_at else None,
                "updated_at": video.updated_at.isoformat() if video.updated_at else None
            }
            items.append(item)
        
        return {
            "success": True,
            "message": "获取热门视频列表成功",
            "data": {
                "items": items,
                "total": len(items)
            }
        }
    except Exception as e:
        logger.error(f"获取热门视频列表失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"服务器内部错误: {str(e)}")