select
  ntile(5)
    over(partition by col7 order by col0) as "ntile"
from
  dfs."%s/window/3648.parquet"
