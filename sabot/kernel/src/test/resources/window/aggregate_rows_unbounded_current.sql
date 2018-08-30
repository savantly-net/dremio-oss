SELECT
	COUNT(*) OVER(PARTITION BY position_id ORDER BY sub ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS "count"
FROM
	%s
