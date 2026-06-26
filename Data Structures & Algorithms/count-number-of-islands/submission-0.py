class Solution:
    def numIslands(self, grid: List[List[str]]) -> int:
        if not grid:
            return 0
        res = 0
        row, col = len(grid), len(grid[0])
        visited = set()


        def bfs(r,c):
            q = deque()
            q.append((r,c))
            visited.add((r,c))
            while q:

                rows, column = q.popleft()

                directions = [[1,0],[-1,0],[0,1],[0,-1]]

                for dr, dc in directions:
                    nr = rows + dr
                    nc = column + dc
                    if 0 <= nr < row and 0 <= nc < col and grid[nr][nc] == "1" and (nr, nc) not in visited: 
                        visited.add((nr,nc))
                        q.append((nr,nc))
                        

            

        for r in range(row):
            for c in range(col):
                if grid[r][c] == "1" and (r,c) not in visited:
                    
                    bfs(r,c)
                    res += 1
                    
        
        return res