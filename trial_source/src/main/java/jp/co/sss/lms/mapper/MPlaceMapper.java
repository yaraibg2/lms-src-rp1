package jp.co.sss.lms.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import jp.co.sss.lms.entity.MPlace;

/**
 * 会場マスタのマッパー
 * @author 東京ITスクール
 */
@Mapper
public interface MPlaceMapper {
	/**
	 * 全件検索
	 * @return
	 */
	List<MPlace> findAll();
}
